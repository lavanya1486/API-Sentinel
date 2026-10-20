package dev.apisentinel.service;

import dev.apisentinel.domain.AlertConfiguration;
import dev.apisentinel.domain.MonitoredEndpoint;
import dev.apisentinel.domain.NotificationDelivery;
import dev.apisentinel.dto.AlertDtos;
import dev.apisentinel.repository.AlertConfigurationRepository;
import dev.apisentinel.repository.NotificationDeliveryRepository;
import dev.apisentinel.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Arrays;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {
    private static final List<String> DEFAULT_EVENTS = List.of("INCIDENT_OPENED", "INCIDENT_RESOLVED");
    private static final Set<String> ALLOWED_EVENTS = Set.copyOf(DEFAULT_EVENTS);
    private final EndpointService endpointService;
    private final AlertConfigurationRepository alerts;
    private final NotificationDeliveryRepository deliveries;
    private final SecretCrypto crypto;
    private final UrlSecurityValidator urlSecurity;

    public AlertService(EndpointService endpointService, AlertConfigurationRepository alerts,
                       NotificationDeliveryRepository deliveries, SecretCrypto crypto,
                       UrlSecurityValidator urlSecurity) {
        this.endpointService = endpointService;
        this.alerts = alerts;
        this.deliveries = deliveries;
        this.crypto = crypto;
        this.urlSecurity = urlSecurity;
    }

    @Transactional(readOnly = true)
    public AlertDtos.AlertResponse get(UUID ownerId, UUID endpointId) {
        MonitoredEndpoint endpoint = endpointService.getOwned(ownerId, endpointId);
        AlertConfiguration config = alerts.findByEndpoint_Id(endpointId).orElse(null);
        return response(config, endpoint);
    }

    @Transactional
    public AlertDtos.AlertResponse save(UUID ownerId, UUID endpointId, AlertDtos.AlertRequest request) {
        MonitoredEndpoint endpoint = endpointService.getOwned(ownerId, endpointId);
        AlertConfiguration config = alerts.findByEndpoint_Id(endpointId).orElse(null);
        boolean enabled = request.enabled() != null ? request.enabled() : config != null && config.isEnabled();
        String ciphertext = config == null ? null : config.getWebhookUrlCiphertext();
        if (request.webhookUrl() != null && !request.webhookUrl().isBlank()) {
            try {
                urlSecurity.validate(request.webhookUrl(), true); // Webhooks are HTTPS-only and DNS-pinned to public addresses.
            } catch (java.io.IOException ex) {
                throw ApiException.badRequest("UNSAFE_WEBHOOK", "Webhook must be a valid HTTPS URL on a public host");
            }
            ciphertext = crypto.encrypt(request.webhookUrl());
        }
        if (enabled && ciphertext == null) {
            throw ApiException.badRequest("WEBHOOK_REQUIRED", "Add an HTTPS webhook URL before enabling this alert");
        }
        List<String> eventTypes = request.eventTypes() == null
            ? (config == null ? DEFAULT_EVENTS : parseEvents(config.getEventTypes()))
            : validateEvents(request.eventTypes());
        String joinedEvents = eventTypes.stream().sorted().collect(Collectors.joining(","));
        if (config == null) config = new AlertConfiguration(endpoint, ciphertext, enabled, joinedEvents);
        else {
            config.setWebhookUrlCiphertext(ciphertext);
            config.setEnabled(enabled);
            config.setEventTypes(joinedEvents);
        }
        return response(alerts.save(config), endpoint);
    }

    @Transactional(readOnly = true)
    public List<AlertDtos.AlertPageItem> list(UUID ownerId) {
        return alerts.findAllForOwner(ownerId).stream().map(config -> new AlertDtos.AlertPageItem(
            config.getId(), config.getEndpoint().getId(), config.getEndpoint().getName(), config.isEnabled(),
            config.getWebhookUrlCiphertext() != null, parseEvents(config.getEventTypes()), config.getUpdatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public AlertDtos.NotificationPage notifications(UUID ownerId, int page, int size) {
        validatePage(page, size);
        Page<NotificationDelivery> rows = deliveries.findAllForOwner(ownerId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new AlertDtos.NotificationPage(rows.getContent().stream().map(AlertService::notification).toList(),
            rows.getNumber(), rows.getSize(), rows.getTotalElements(), rows.getTotalPages(), rows.hasNext());
    }

    @Transactional(readOnly = true)
    public AlertDtos.NotificationResponse notification(UUID ownerId, UUID id) {
        NotificationDelivery item = deliveries.findByIdAndAlertConfiguration_Endpoint_Owner_Id(id, ownerId)
            .orElseThrow(() -> ApiException.notFound("Notification"));
        if (item.getAlertConfiguration().getEndpoint().getArchivedAt() != null) throw ApiException.notFound("Notification");
        return notification(item);
    }

    private static AlertDtos.AlertResponse response(AlertConfiguration config, MonitoredEndpoint endpoint) {
        if (config == null) return new AlertDtos.AlertResponse(null, endpoint.getId(), endpoint.getName(), false,
            false, DEFAULT_EVENTS, null);
        return new AlertDtos.AlertResponse(config.getId(), endpoint.getId(), endpoint.getName(), config.isEnabled(),
            config.getWebhookUrlCiphertext() != null, parseEvents(config.getEventTypes()), config.getUpdatedAt());
    }

    private static AlertDtos.NotificationResponse notification(NotificationDelivery item) {
        var endpoint = item.getAlertConfiguration().getEndpoint();
        return new AlertDtos.NotificationResponse(item.getId(), endpoint.getId(), endpoint.getName(),
            item.getEventType(), item.getStatus(), item.getAttempts(), item.getLastError(), item.getCreatedAt(),
            item.getLastAttemptAt(), item.getNextAttemptAt(), item.getDeliveredAt());
    }

    private static List<String> validateEvents(List<String> requested) {
        if (requested.isEmpty()) throw ApiException.badRequest("INVALID_EVENT_TYPES", "Select at least one incident event");
        if (requested.size() > 2) throw ApiException.badRequest("INVALID_EVENT_TYPES", "At most two event types are supported");
        List<String> normalized = requested.stream().map(value -> value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT)).toList();
        if (normalized.stream().anyMatch(value -> !ALLOWED_EVENTS.contains(value)) || normalized.stream().distinct().count() != normalized.size()) {
            throw ApiException.badRequest("INVALID_EVENT_TYPES", "Allowed events are INCIDENT_OPENED and INCIDENT_RESOLVED");
        }
        return normalized;
    }

    private static List<String> parseEvents(String stored) {
        return Arrays.stream(stored.split(",")).map(String::trim).filter(value -> ALLOWED_EVENTS.contains(value)).sorted().toList();
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || page > 100_000) throw ApiException.badRequest("INVALID_PAGE", "Page must be between 0 and 100000");
        if (size < 1 || size > 100) throw ApiException.badRequest("INVALID_SIZE", "Page size must be between 1 and 100");
    }
}
