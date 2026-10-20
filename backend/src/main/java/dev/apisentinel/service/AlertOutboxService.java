package dev.apisentinel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.apisentinel.domain.AlertConfiguration;
import dev.apisentinel.domain.Incident;
import dev.apisentinel.domain.NotificationDelivery;
import dev.apisentinel.repository.AlertConfigurationRepository;
import dev.apisentinel.repository.NotificationDeliveryRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertOutboxService {
    private final AlertConfigurationRepository alerts;
    private final NotificationDeliveryRepository deliveries;
    private final ObjectMapper mapper;

    public AlertOutboxService(AlertConfigurationRepository alerts, NotificationDeliveryRepository deliveries,
                              ObjectMapper mapper) {
        this.alerts = alerts;
        this.deliveries = deliveries;
        this.mapper = mapper;
    }

    @Transactional
    public void enqueue(Incident incident, String eventType) {
        AlertConfiguration alert = alerts.findByEndpoint_Id(incident.getEndpoint().getId()).orElse(null);
        if (alert == null || !alert.isEnabled() || !eventTypes(alert).contains(eventType)) return;
        // The webhook payload is deliberately credential-free: no URL, request headers, response body, or failure text.
        var payload = new EventPayload(eventType, incident.getId(), incident.getEndpoint().getId(),
            incident.getStatus(), Instant.now());
        try {
            deliveries.save(new NotificationDelivery(alert, eventType, mapper.writeValueAsString(payload)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not enqueue notification");
        }
    }

    private static Set<String> eventTypes(AlertConfiguration alert) {
        return Arrays.stream(alert.getEventTypes().split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
    }

    public record EventPayload(String event, UUID incidentId, UUID endpointId, String status, Instant occurredAt) { }
}
