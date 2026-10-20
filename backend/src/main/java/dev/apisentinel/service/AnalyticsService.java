package dev.apisentinel.service;

import dev.apisentinel.domain.MonitoredEndpoint;
import dev.apisentinel.dto.AnalyticsDtos;
import dev.apisentinel.repository.HealthCheckRepository;
import dev.apisentinel.repository.IncidentRepository;
import dev.apisentinel.repository.MonitoredEndpointRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
    private final HealthCheckRepository checks;
    private final MonitoredEndpointRepository endpoints;
    private final IncidentRepository incidents;
    private final EndpointService endpointService;

    public AnalyticsService(HealthCheckRepository checks, MonitoredEndpointRepository endpoints,
                            IncidentRepository incidents, EndpointService endpointService) {
        this.checks = checks;
        this.endpoints = endpoints;
        this.incidents = incidents;
        this.endpointService = endpointService;
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.OverviewResponse overview(UUID ownerId, String range, UUID endpointId) {
        String selectedRange = range == null ? "24h" : range.toLowerCase(java.util.Locale.ROOT);
        Instant now = Instant.now();
        Instant from = TimeRanges.from(selectedRange, now);
        if (endpointId != null) endpointService.getOwned(ownerId, endpointId);
        HealthCheckRepository.AggregateView aggregate = checks.aggregate(ownerId, from, endpointId);
        long totalChecks = number(aggregate.getTotalCount());
        long success = number(aggregate.getSuccessCount());
        long failures = number(aggregate.getFailureCount());
        double uptime = percent(success, totalChecks);
        double errorRate = percent(failures, totalChecks);
        List<AnalyticsDtos.SeriesPoint> series = checks.series(ownerId, from, endpointId, TimeRanges.bucket(selectedRange))
            .stream().map(point -> {
                Instant bucketAt = point.getBucketAt();
                return new AnalyticsDtos.SeriesPoint(bucketAt == null ? from : bucketAt,
                    decimal(point.getAverageLatencyMs()), number(point.getSuccessCount()),
                    number(point.getFailureCount()), number(point.getCheckCount()));
            }).toList();
        return new AnalyticsDtos.OverviewResponse(selectedRange, from, now,
            endpoints.countByOwner_IdAndArchivedAtIsNull(ownerId),
            endpoints.countByOwner_IdAndEnabledTrueAndArchivedAtIsNull(ownerId),
            incidents.countByEndpoint_Owner_IdAndStatusAndEndpoint_ArchivedAtIsNull(ownerId, "OPEN"),
            totalChecks, success, failures, uptime, errorRate,
            decimal(aggregate.getAverageLatencyMs()), decimal(aggregate.getP95LatencyMs()),
            decimal(aggregate.getP99LatencyMs()), series);
    }

    private static long number(Number value) { return value == null ? 0 : value.longValue(); }
    private static double decimal(Number value) { return value == null ? 0 : value.doubleValue(); }
    private static double percent(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : Math.round((double) numerator * 10_000.0 / denominator) / 100.0;
    }
}
