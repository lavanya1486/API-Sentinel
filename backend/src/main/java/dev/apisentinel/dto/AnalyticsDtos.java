package dev.apisentinel.dto;

import java.time.Instant;
import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() { }

    public record OverviewResponse(
        String range,
        Instant from,
        Instant to,
        long totalEndpoints,
        long activeEndpoints,
        long openIncidents,
        long totalChecks,
        long successfulChecks,
        long failedChecks,
        double uptimePercent,
        double errorRatePercent,
        double averageLatencyMs,
        double p95LatencyMs,
        double p99LatencyMs,
        List<SeriesPoint> series
    ) { }

    public record SeriesPoint(Instant bucketAt, double averageLatencyMs, long successfulChecks,
                              long failedChecks, long totalChecks) { }
}
