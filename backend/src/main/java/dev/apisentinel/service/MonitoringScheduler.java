package dev.apisentinel.service;

import dev.apisentinel.domain.MonitoredEndpoint;
import dev.apisentinel.repository.MonitoredEndpointRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;

@Component
public class MonitoringScheduler {
    private static final Logger log = LoggerFactory.getLogger(MonitoringScheduler.class);
    private static final long CLAIM_LEASE_SECONDS = 300;
    private final MonitoredEndpointRepository endpoints;
    private final MonitoringService monitoring;
    private final TaskExecutor executor;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public MonitoringScheduler(MonitoredEndpointRepository endpoints, MonitoringService monitoring,
                               @org.springframework.beans.factory.annotation.Qualifier("monitorExecutor") TaskExecutor executor) {
        this.endpoints = endpoints;
        this.monitoring = monitoring;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${app.monitoring.scan-delay-ms:5000}")
    @Transactional
    public void scan() {
        Instant now = Instant.now();
        for (MonitoredEndpoint endpoint : endpoints.findDue(now, PageRequest.of(0, 50))) {
            UUID id = endpoint.getId();
            if (!inFlight.add(id)) continue;
            if (endpoints.claimDue(id, now, now.plusSeconds(CLAIM_LEASE_SECONDS)) == 0) {
                inFlight.remove(id);
                continue;
            }
            if (!submit(id)) endpoints.releaseClaim(id, now.plusSeconds(15));
        }
    }

    @Transactional
    public boolean requestManual(UUID endpointId) {
        Instant now = Instant.now();
        if (!inFlight.add(endpointId)) return false;
        if (endpoints.claimManual(endpointId, now, now.plusSeconds(CLAIM_LEASE_SECONDS)) == 0) {
            inFlight.remove(endpointId);
            return false;
        }
        if (!submit(endpointId)) {
            endpoints.releaseClaim(endpointId, now.plusSeconds(15));
            return false;
        }
        return true;
    }

    private boolean submit(UUID id) {
        try {
            executor.execute(() -> {
                try {
                    monitoring.runCheck(id);
                } catch (RuntimeException ex) {
                    // Never log a raw exception message: HTTP libraries may include sensitive URLs.
                    log.error("Monitor worker failed for endpoint {} ({})", id, ex.getClass().getSimpleName());
                } finally {
                    inFlight.remove(id);
                }
            });
            return true;
        } catch (TaskRejectedException ex) {
            inFlight.remove(id);
            log.warn("Monitor queue is full; deferred endpoint {}", id);
            return false;
        }
    }
}
