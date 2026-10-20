package dev.apisentinel.web;

import dev.apisentinel.dto.AlertDtos;
import dev.apisentinel.security.ApiUserPrincipal;
import dev.apisentinel.service.AlertService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlertController {
    private final AlertService alerts;
    public AlertController(AlertService alerts) { this.alerts = alerts; }

    @GetMapping("/api/v1/endpoints/{endpointId}/alert")
    public AlertDtos.AlertResponse get(@AuthenticationPrincipal ApiUserPrincipal user, @PathVariable UUID endpointId) {
        return alerts.get(user.id(), endpointId);
    }

    @PutMapping("/api/v1/endpoints/{endpointId}/alert")
    public AlertDtos.AlertResponse save(@AuthenticationPrincipal ApiUserPrincipal user, @PathVariable UUID endpointId,
        @Valid @RequestBody AlertDtos.AlertRequest request) {
        return alerts.save(user.id(), endpointId, request);
    }

    @GetMapping("/api/v1/alerts")
    public List<AlertDtos.AlertPageItem> list(@AuthenticationPrincipal ApiUserPrincipal user) {
        return alerts.list(user.id());
    }

    @GetMapping("/api/v1/notifications")
    public AlertDtos.NotificationPage notifications(@AuthenticationPrincipal ApiUserPrincipal user,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return alerts.notifications(user.id(), page, size);
    }

    @GetMapping("/api/v1/notifications/{id}")
    public AlertDtos.NotificationResponse notification(@AuthenticationPrincipal ApiUserPrincipal user, @PathVariable UUID id) {
        return alerts.notification(user.id(), id);
    }
}
