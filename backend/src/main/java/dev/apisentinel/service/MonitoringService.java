package dev.apisentinel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.apisentinel.domain.MonitoredEndpoint;
import dev.apisentinel.repository.MonitoredEndpointRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MonitoringService {
    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);
    private static final TypeReference<Map<String, String>> HEADER_MAP = new TypeReference<>() { };
    private final MonitoredEndpointRepository endpoints;
    private final SecretCrypto crypto;
    private final ObjectMapper mapper;
    private final SafeHttpClient http;
    private final RetryingCheckRunner runner;
    private final IncidentStateService state;

    public MonitoringService(MonitoredEndpointRepository endpoints, SecretCrypto crypto, ObjectMapper mapper,
                             SafeHttpClient http, RetryingCheckRunner runner, IncidentStateService state) {
        this.endpoints = endpoints;
        this.crypto = crypto;
        this.mapper = mapper;
        this.http = http;
        this.runner = runner;
        this.state = state;
    }

    public void runCheck(UUID endpointId) {
        MonitoredEndpoint endpoint = endpoints.findById(endpointId).orElse(null);
        if (endpoint == null) return;
        int interval = endpoint.getIntervalSeconds();
        RetryingCheckRunner.Result result = null;
        try {
            if (endpoint.getArchivedAt() != null || !endpoint.isEnabled()) {
                endpoints.releaseClaim(endpointId, Instant.now().plusSeconds(Math.max(10, interval)));
                return;
            }
            String url = crypto.decrypt(endpoint.getUrlCiphertext());
            Map<String, String> headers = decryptHeaders(endpoint.getHeadersCiphertext());
            Set<Integer> expected = Arrays.stream(endpoint.getExpectedStatusCodes().split(","))
                .map(Integer::parseInt).collect(Collectors.toUnmodifiableSet());
            result = runner.run(() -> http.check(url, endpoint.getMethod(), headers, endpoint.getTimeoutMs()).statusCode(),
                expected, endpoint.getRetryCount(), endpoint.getRetryBackoffMs(), endpoint.getTimeoutMs());
        } catch (Exception ex) {
            // Do not emit exception messages, since clients may include full URLs or header material.
            result = new RetryingCheckRunner.Result(false, null, 0, 1,
                ex instanceof BlockedTargetException ? "Target blocked by SSRF policy" : "Monitor configuration unavailable");
            log.warn("Monitor execution failed for endpoint {} ({})", endpointId, ex.getClass().getSimpleName());
        }
        try {
            if (result != null) state.record(endpointId, result);
        } catch (Exception ex) {
            log.error("Could not persist monitor result for endpoint {} ({})", endpointId, ex.getClass().getSimpleName());
        } finally {
            endpoints.releaseClaim(endpointId, Instant.now().plusSeconds(Math.max(10, interval)));
        }
    }

    private Map<String, String> decryptHeaders(String ciphertext) throws java.io.IOException {
        if (ciphertext == null) return Map.of();
        Map<String, String> decoded = mapper.readValue(crypto.decrypt(ciphertext), HEADER_MAP);
        return decoded == null ? Map.of() : Map.copyOf(decoded);
    }
}
