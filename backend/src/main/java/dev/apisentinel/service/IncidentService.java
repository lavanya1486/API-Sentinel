package dev.apisentinel.service;

import dev.apisentinel.domain.Incident;
import dev.apisentinel.dto.IncidentDtos;
import dev.apisentinel.repository.IncidentEventRepository;
import dev.apisentinel.repository.IncidentRepository;
import dev.apisentinel.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {
    private final IncidentRepository incidents;
    private final IncidentEventRepository events;

    public IncidentService(IncidentRepository incidents, IncidentEventRepository events) {
        this.incidents = incidents;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public IncidentDtos.IncidentPage list(UUID ownerId, String state, UUID endpointId, int page, int size) {
        String status = normalizeStatus(state);
        validatePage(page, size);
        Page<Incident> rows = incidents.searchOwned(ownerId, status, endpointId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt")));
        return new IncidentDtos.IncidentPage(rows.getContent().stream().map(IncidentService::toResponse).toList(),
            rows.getNumber(), rows.getSize(), rows.getTotalElements(), rows.getTotalPages(), rows.hasNext());
    }

    @Transactional(readOnly = true)
    public IncidentDtos.IncidentDetail detail(UUID ownerId, UUID incidentId) {
        Incident incident = incidents.findByIdAndEndpoint_Owner_IdAndEndpoint_ArchivedAtIsNull(incidentId, ownerId)
            .orElseThrow(() -> ApiException.notFound("Incident"));
        var items = events.findByIncident_IdOrderByOccurredAtAsc(incidentId).stream()
            .map(event -> new IncidentDtos.EventResponse(event.getId(), event.getEventType(), event.getDetail(), event.getOccurredAt()))
            .toList();
        return new IncidentDtos.IncidentDetail(toResponse(incident), items);
    }

    private static IncidentDtos.IncidentResponse toResponse(Incident incident) {
        Instant end = incident.getResolvedAt() == null ? Instant.now() : incident.getResolvedAt();
        long duration = Math.max(0, Duration.between(incident.getStartedAt(), end).getSeconds());
        return new IncidentDtos.IncidentResponse(incident.getId(), incident.getEndpoint().getId(),
            incident.getEndpoint().getName(), incident.getStatus(), incident.getTitle(), incident.getFailureDetail(),
            incident.getStartedAt(), incident.getResolvedAt(), duration);
    }

    private static String normalizeStatus(String state) {
        if (state == null || state.isBlank() || state.equalsIgnoreCase("all")) return null;
        String status = state.toUpperCase(java.util.Locale.ROOT);
        if (!status.equals("OPEN") && !status.equals("RESOLVED")) {
            throw ApiException.badRequest("INVALID_INCIDENT_STATUS", "Incident status must be OPEN or RESOLVED");
        }
        return status;
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || page > 100_000) throw ApiException.badRequest("INVALID_PAGE", "Page must be between 0 and 100000");
        if (size < 1 || size > 100) throw ApiException.badRequest("INVALID_SIZE", "Page size must be between 1 and 100");
    }
}
