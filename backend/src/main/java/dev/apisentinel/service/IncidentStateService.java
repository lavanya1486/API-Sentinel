package dev.apisentinel.service;

import dev.apisentinel.domain.HealthCheck;
import dev.apisentinel.domain.Incident;
import dev.apisentinel.domain.IncidentEvent;
import dev.apisentinel.domain.MonitoredEndpoint;
import dev.apisentinel.repository.HealthCheckRepository;
import dev.apisentinel.repository.IncidentEventRepository;
import dev.apisentinel.repository.IncidentRepository;
import dev.apisentinel.repository.MonitoredEndpointRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentStateService {
    private final MonitoredEndpointRepository endpoints;
    private final HealthCheckRepository checks;
    private final IncidentRepository incidents;
    private final IncidentEventRepository events;
    private final AlertOutboxService outbox;

    public IncidentStateService(MonitoredEndpointRepository endpoints, HealthCheckRepository checks,
                                IncidentRepository incidents, IncidentEventRepository events,
                                AlertOutboxService outbox) {
        this.endpoints = endpoints;
        this.checks = checks;
        this.incidents = incidents;
        this.events = events;
        this.outbox = outbox;
    }

    @Transactional
    public void record(UUID endpointId, RetryingCheckRunner.Result result) {
        MonitoredEndpoint endpoint = endpoints.findById(endpointId).orElse(null);
        if (endpoint == null) return;
        String failureDetail = result.successful() ? null : safeFailure(result.failureDetail());
        checks.save(new HealthCheck(endpoint, result.responseTimeMs(), result.statusCode(), result.successful(),
            failureDetail, Math.max(1, Math.min(4, result.attempts()))));

        if (!result.successful()) {
            endpoint.setConsecutiveFailures(endpoint.getConsecutiveFailures() + 1);
            endpoint.setConsecutiveSuccesses(0);
            Incident open = incidents.findFirstByEndpoint_IdAndStatus(endpointId, "OPEN").orElse(null);
            if (open == null && endpoint.getConsecutiveFailures() >= endpoint.getFailureThreshold()) {
                Incident incident = incidents.save(new Incident(endpoint, endpoint.getName() + " is failing", failureDetail));
                events.save(new IncidentEvent(incident, "OPENED",
                    "Incident opened after " + endpoint.getConsecutiveFailures() + " consecutive failed checks"));
                outbox.enqueue(incident, "INCIDENT_OPENED");
            } else if (open != null) {
                open.setFailureDetail(failureDetail);
            }
        } else {
            endpoint.setConsecutiveSuccesses(endpoint.getConsecutiveSuccesses() + 1);
            endpoint.setConsecutiveFailures(0);
            Incident open = incidents.findFirstByEndpoint_IdAndStatus(endpointId, "OPEN").orElse(null);
            if (open != null && endpoint.getConsecutiveSuccesses() == 1) {
                events.save(new IncidentEvent(open, "RECOVERY_OBSERVED", "A successful check was observed; awaiting recovery threshold"));
            }
            if (open != null && endpoint.getConsecutiveSuccesses() >= endpoint.getRecoveryThreshold()) {
                Instant now = Instant.now();
                open.setStatus("RESOLVED");
                open.setResolvedAt(now);
                events.save(new IncidentEvent(open, "RESOLVED",
                    "Incident resolved after " + endpoint.getConsecutiveSuccesses() + " consecutive successful checks"));
                outbox.enqueue(open, "INCIDENT_RESOLVED");
            }
        }
        endpoints.save(endpoint);
    }

    private static String safeFailure(String detail) {
        if (detail == null || detail.isBlank()) return "Check failed";
        // The runner only emits allow-listed status/error categories. Keep a final storage bound as defense in depth.
        return detail.length() > 190 ? detail.substring(0, 190) : detail;
    }
}
