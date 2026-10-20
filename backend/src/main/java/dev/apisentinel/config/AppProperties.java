package dev.apisentinel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Security security, Cors cors, Monitoring monitoring, Notifications notifications) {
    public record Security(String jwtSecret, String encryptionKey, long tokenTtlMinutes, boolean trustProxyHeaders) { }
    public record Cors(String allowedOrigins) { }
    public record Monitoring(long scanDelayMs, int maxConcurrentChecks, int historyRetentionDays, int maxResponseBytes) { }
    public record Notifications(int maxAttempts, long baseBackoffSeconds) { }
}
