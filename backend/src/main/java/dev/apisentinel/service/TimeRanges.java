package dev.apisentinel.service;

import dev.apisentinel.web.ApiException;
import java.time.Duration;
import java.time.Instant;

public final class TimeRanges {
    private TimeRanges() { }

    public static Duration duration(String value) {
        return switch (value == null ? "24h" : value.toLowerCase(java.util.Locale.ROOT)) {
            case "1h" -> Duration.ofHours(1);
            case "24h" -> Duration.ofHours(24);
            case "7d" -> Duration.ofDays(7);
            case "30d" -> Duration.ofDays(30);
            case "90d" -> Duration.ofDays(90);
            default -> throw ApiException.badRequest("INVALID_RANGE", "Range must be one of 1h, 24h, 7d, 30d, or 90d");
        };
    }

    public static String bucket(String range) {
        return switch (range == null ? "24h" : range.toLowerCase(java.util.Locale.ROOT)) {
            case "1h", "24h" -> "hour";
            case "7d", "30d", "90d" -> "day";
            default -> throw ApiException.badRequest("INVALID_RANGE", "Range must be one of 1h, 24h, 7d, 30d, or 90d");
        };
    }

    public static Instant from(String range, Instant now) { return now.minus(duration(range)); }
}
