package dev.apisentinel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.apisentinel.dto.AnalyticsDtos;
import dev.apisentinel.repository.HealthCheckRepository;
import dev.apisentinel.repository.IncidentRepository;
import dev.apisentinel.repository.MonitoredEndpointRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {
    @Test
    void computesCheckBasedUptimeAndErrorRatesAndUsesDatabaseLatencyPercentiles() {
        HealthCheckRepository checks = mock(HealthCheckRepository.class);
        MonitoredEndpointRepository endpoints = mock(MonitoredEndpointRepository.class);
        IncidentRepository incidents = mock(IncidentRepository.class);
        EndpointService endpointService = mock(EndpointService.class);
        UUID ownerId = UUID.randomUUID();
        HealthCheckRepository.AggregateView aggregate = mock(HealthCheckRepository.AggregateView.class);
        when(checks.aggregate(eq(ownerId), any(Instant.class), isNull())).thenReturn(aggregate);
        when(aggregate.getTotalCount()).thenReturn(4L);
        when(aggregate.getSuccessCount()).thenReturn(3L);
        when(aggregate.getFailureCount()).thenReturn(1L);
        when(aggregate.getAverageLatencyMs()).thenReturn(new BigDecimal("125.50"));
        when(aggregate.getP95LatencyMs()).thenReturn(240.0);
        when(aggregate.getP99LatencyMs()).thenReturn(250.0);
        when(checks.series(eq(ownerId), any(Instant.class), isNull(), eq("day"))).thenReturn(List.of());
        when(endpoints.countByOwner_IdAndArchivedAtIsNull(ownerId)).thenReturn(2L);
        when(endpoints.countByOwner_IdAndEnabledTrueAndArchivedAtIsNull(ownerId)).thenReturn(1L);
        when(incidents.countByEndpoint_Owner_IdAndStatusAndEndpoint_ArchivedAtIsNull(ownerId, "OPEN")).thenReturn(1L);

        AnalyticsDtos.OverviewResponse result = new AnalyticsService(checks, endpoints, incidents, endpointService)
            .overview(ownerId, "7d", null);

        assertEquals(75.0, result.uptimePercent());
        assertEquals(25.0, result.errorRatePercent());
        assertEquals(125.5, result.averageLatencyMs());
        assertEquals(240.0, result.p95LatencyMs());
        assertEquals(250.0, result.p99LatencyMs());
        assertEquals(2, result.totalEndpoints());
        assertEquals(1, result.openIncidents());
        assertEquals("7d", result.range());
    }
}
