package dev.apisentinel.web;

import dev.apisentinel.dto.IncidentDtos;
import dev.apisentinel.security.ApiUserPrincipal;
import dev.apisentinel.service.IncidentService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {
    private final IncidentService incidents;
    public IncidentController(IncidentService incidents) { this.incidents = incidents; }

    @GetMapping
    public IncidentDtos.IncidentPage list(@AuthenticationPrincipal ApiUserPrincipal user,
        @RequestParam(defaultValue = "all") String status, @RequestParam(required = false) UUID endpointId,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return incidents.list(user.id(), status, endpointId, page, size);
    }

    @GetMapping("/{id}")
    public IncidentDtos.IncidentDetail detail(@AuthenticationPrincipal ApiUserPrincipal user, @PathVariable UUID id) {
        return incidents.detail(user.id(), id);
    }
}
