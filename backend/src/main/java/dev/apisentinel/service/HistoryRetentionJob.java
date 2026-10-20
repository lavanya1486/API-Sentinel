package dev.apisentinel.service;

import dev.apisentinel.config.AppProperties;
import dev.apisentinel.repository.HealthCheckRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HistoryRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(HistoryRetentionJob.class);
    private final HealthCheckRepository checks;
    private final AppProperties properties;

    public HistoryRetentionJob(HealthCheckRepository checks, AppProperties properties) {
        this.checks = checks;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.monitoring.retention-cron:0 25 3 * * *}")
    public void prune() {
        int days = Math.max(1, Math.min(3650, properties.monitoring().historyRetentionDays()));
        int deleted = checks.deleteOlderThan(Instant.now().minus(days, ChronoUnit.DAYS));
        if (deleted > 0) log.info("Pruned {} expired health-check rows", deleted);
    }
}
