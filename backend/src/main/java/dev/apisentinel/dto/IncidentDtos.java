package dev.apisentinel.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class IncidentDtos {
    private IncidentDtos() { }

    public record EventResponse(UUID id, String eventType, String detail, Instant occurredAt) { }
    public record IncidentResponse(UUID id, UUID endpointId, String endpointName, String status, String title,
                                  String failureDetail, Instant startedAt, Instant resolvedAt,
                                  long durationSeconds) { }
    public record IncidentDetail(IncidentResponse incident, List<EventResponse> events) { }
    public record IncidentPage(List<IncidentResponse> content, int page, int size, long totalElements,
                               int totalPages, boolean hasNext) { }
}
