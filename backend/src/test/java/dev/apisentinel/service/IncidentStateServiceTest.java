package dev.apisentinel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.apisentinel.domain.AppUser;
import dev.apisentinel.domain.Incident;
import dev.apisentinel.domain.IncidentEvent;
import dev.apisentinel.domain.MonitoredEndpoint;
import dev.apisentinel.repository.HealthCheckRepository;
import dev.apisentinel.repository.IncidentEventRepository;
import dev.apisentinel.repository.IncidentRepository;
import dev.apisentinel.repository.MonitoredEndpointRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class IncidentStateServiceTest {
    @Test
    void opensOnceAfterFailureThresholdAndResolvesAfterRecoveryThreshold() {
        MonitoredEndpointRepository endpoints = mock(MonitoredEndpointRepository.class);
        HealthCheckRepository checks = mock(HealthCheckRepository.class);
        IncidentRepository incidents = mock(IncidentRepository.class);
        IncidentEventRepository events = mock(IncidentEventRepository.class);
        AlertOutboxService outbox = mock(AlertOutboxService.class);
        AppUser owner = new AppUser("owner@example.net", "bcrypt-hash");
        MonitoredEndpoint endpoint = new MonitoredEndpoint(owner, "Checkout API", "api.acme.net",
            "ciphertext", null, "GET", "200", 5000, 60, 1, 250, 2, 2);
        AtomicReference<Incident> active = new AtomicReference<>();
        when(endpoints.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));
        when(endpoints.save(any(MonitoredEndpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(checks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(events.save(any(IncidentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(incidents.findFirstByEndpoint_IdAndStatus(eq(endpoint.getId()), eq("OPEN")))
            .thenAnswer(invocation -> Optional.ofNullable(active.get()).filter(item -> item.getStatus().equals("OPEN")));
        when(incidents.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            active.set(incident);
            return incident;
        });
        IncidentStateService service = new IncidentStateService(endpoints, checks, incidents, events, outbox);

        service.record(endpoint.getId(), failed("Connection failed"));
        assertEquals(1, endpoint.getConsecutiveFailures());
        org.junit.jupiter.api.Assertions.assertTrue(active.get() == null);

        service.record(endpoint.getId(), failed("Connection failed"));
        Incident incident = active.get();
        assertNotNull(incident);
        assertEquals("OPEN", incident.getStatus());
        verify(outbox).enqueue(incident, "INCIDENT_OPENED");

        service.record(endpoint.getId(), succeeded());
        assertEquals("OPEN", incident.getStatus());
        assertEquals(1, endpoint.getConsecutiveSuccesses());

        service.record(endpoint.getId(), succeeded());
        assertEquals("RESOLVED", incident.getStatus());
        assertNotNull(incident.getResolvedAt());
        verify(outbox).enqueue(incident, "INCIDENT_RESOLVED");
        verify(checks, times(4)).save(any());
    }

    private static RetryingCheckRunner.Result failed(String detail) {
        return new RetryingCheckRunner.Result(false, 503, 35, 2, detail);
    }

    private static RetryingCheckRunner.Result succeeded() {
        return new RetryingCheckRunner.Result(true, 200, 18, 1, null);
    }
}
