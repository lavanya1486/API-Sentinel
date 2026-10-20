package dev.apisentinel.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EndpointDtos {
    private EndpointDtos() { }

    public record CreateEndpointRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 2048) String url,
        @NotBlank @Pattern(regexp = "GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS") String method,
        @NotEmpty @Size(max = 10) List<@NotNull @Min(100) @Max(599) Integer> expectedStatusCodes,
        @NotNull @Min(500) @Max(30000) Integer timeoutMs,
        @NotNull @Min(10) @Max(86400) Integer intervalSeconds,
        @NotNull @Min(0) @Max(3) Integer retryCount,
        @NotNull @Min(100) @Max(10000) Integer retryBackoffMs,
        @NotNull @Min(1) @Max(20) Integer failureThreshold,
        @NotNull @Min(1) @Max(20) Integer recoveryThreshold,
        @Size(max = 20) Map<String, String> headers
    ) { }

    public record UpdateEndpointRequest(
        @Size(max = 120) String name,
        @Size(max = 2048) String url,
        @Pattern(regexp = "GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS") String method,
        @Size(max = 10) List<@Min(100) @Max(599) Integer> expectedStatusCodes,
        @Min(500) @Max(30000) Integer timeoutMs,
        @Min(10) @Max(86400) Integer intervalSeconds,
        @Min(0) @Max(3) Integer retryCount,
        @Min(100) @Max(10000) Integer retryBackoffMs,
        @Min(1) @Max(20) Integer failureThreshold,
        @Min(1) @Max(20) Integer recoveryThreshold,
        @Size(max = 20) Map<String, String> headers
    ) { }

    public record EndpointResponse(
        UUID id,
        String name,
        String targetDisplay,
        boolean urlHasPath,
        String method,
        List<Integer> expectedStatusCodes,
        int timeoutMs,
        int intervalSeconds,
        int retryCount,
        int retryBackoffMs,
        int failureThreshold,
        int recoveryThreshold,
        List<String> headerNames,
        boolean enabled,
        String healthStatus,
        Long latestResponseTimeMs,
        Integer latestStatusCode,
        String lastFailureDetail,
        Instant lastCheckedAt,
        int consecutiveFailures,
        Instant createdAt,
        Instant updatedAt
    ) { }

    public record EndpointPage(List<EndpointResponse> content, int page, int size, long totalElements,
                               int totalPages, boolean hasNext) { }

    public record HealthCheckResponse(UUID id, Instant checkedAt, long responseTimeMs, Integer statusCode,
                                      boolean successful, String failureDetail, int attemptCount) { }

    public record HealthCheckPage(List<HealthCheckResponse> content, int page, int size, long totalElements,
                                  int totalPages, boolean hasNext) { }
}
