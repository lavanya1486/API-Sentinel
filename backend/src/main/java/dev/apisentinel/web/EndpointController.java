package dev.apisentinel.web;

import dev.apisentinel.dto.EndpointDtos;
import dev.apisentinel.security.ApiUserPrincipal;
import dev.apisentinel.service.EndpointService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/endpoints")
public class EndpointController {
    private final EndpointService endpoints;
    public EndpointController(EndpointService endpoints) { this.endpoints = endpoints; }

    @GetMapping
    public EndpointDtos.EndpointPage list(@AuthenticationPrincipal ApiUserPrincipal user,
        @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "createdAt") String sort,
        @RequestParam(defaultValue = "desc") String direction) {
        return endpoints.list(user.id(), q, page, size, sort, direction);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EndpointDtos.EndpointResponse create(@AuthenticationPrincipal ApiUserPrincipal user,
        @Valid @RequestBody EndpointDtos.CreateEndpointRequest request) {
        return endpoints.create(user.id(), request);
    }

    @GetMapping("/{id}")
    public EndpointDtos.EndpointResponse get(@AuthenticationPrincipal ApiUserPrincipal user, @PathVariable UUID id) {
        return endpoints.get(user.id(), id);
    }

    @PutMapping("/{id}")
    public EndpointDtos.EndpointResponse update(@AuthenticationPrincipal ApiUserPrincipal user, @PathVariable UUID id,
        @Valid @RequestBody EndpointDtos.UpdateEndpointRequest request) {
        return endpoints.update(user.id(), id, request);
    }

    @PostMapping("/{id}/pause")
    public EndpointDtos.EndpointResponse pause(@AuthenticationPrincipal ApiUserPrincipal user, @PathVariable UUID id) {
        return endpoints.setEnabled(user.id(), id, false);
    }

    @PostMapping("/{id}/resume")
    public EndpointDtos.EndpointResponse resume(@AuthenticationPrincipal ApiUserPrincipal user, @PathVariable UUID id) {
        return endpoints.setEnabled(user.id(), id, true);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@AuthenticationPrincipal ApiUserPrincipal user, @PathVariable UUID id) {
        endpoints.archive(user.id(), id);
    }

    @PostMapping("/{id}/check-now")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CheckAccepted checkNow(@AuthenticationPrincipal ApiUserPrincipal user, @PathVariable UUID id) {
        endpoints.requestCheck(user.id(), id);
        return new CheckAccepted(true, Instant.now());
    }

    @GetMapping("/{id}/checks")
    public EndpointDtos.HealthCheckPage checks(@AuthenticationPrincipal ApiUserPrincipal user, @PathVariable UUID id,
        @RequestParam(defaultValue = "24h") String range, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return endpoints.checks(user.id(), id, range, page, size);
    }

    public record CheckAccepted(boolean scheduled, Instant acceptedAt) { }
}
