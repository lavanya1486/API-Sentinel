package dev.apisentinel.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AlertDtos {
    private AlertDtos() { }

    public record AlertRequest(Boolean enabled, @Size(max = 2048) String webhookUrl,
                               @Size(max = 2) List<String> eventTypes) { }

    public record AlertResponse(UUID id, UUID endpointId, String endpointName, boolean enabled,
                                boolean webhookConfigured, List<String> eventTypes, Instant updatedAt) { }

    public record AlertPageItem(UUID id, UUID endpointId, String endpointName, boolean enabled,
                                boolean webhookConfigured, List<String> eventTypes, Instant updatedAt) { }

    public record NotificationResponse(UUID id, UUID endpointId, String endpointName, String eventType,
                                       String status, int attempts, String lastError, Instant createdAt,
                                       Instant lastAttemptAt, Instant nextAttemptAt, Instant deliveredAt) { }

    public record NotificationPage(List<NotificationResponse> content, int page, int size, long totalElements,
                                   int totalPages, boolean hasNext) { }
}
