package dev.apisentinel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.apisentinel.domain.AppUser;
import dev.apisentinel.domain.HealthCheck;
import dev.apisentinel.domain.MonitoredEndpoint;
import dev.apisentinel.dto.EndpointDtos;
import dev.apisentinel.repository.AppUserRepository;
import dev.apisentinel.repository.HealthCheckRepository;
import dev.apisentinel.repository.MonitoredEndpointRepository;
import dev.apisentinel.web.ApiException;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndpointService {
    private static final TypeReference<LinkedHashMap<String, String>> HEADER_MAP = new TypeReference<>() { };
    private final MonitoredEndpointRepository endpoints;
    private final AppUserRepository users;
    private final HealthCheckRepository checks;
    private final UrlSecurityValidator urlSecurity;
    private final SecretCrypto crypto;
    private final ObjectMapper mapper;
    private final MonitoringScheduler scheduler;

    public EndpointService(MonitoredEndpointRepository endpoints, AppUserRepository users, HealthCheckRepository checks,
                           UrlSecurityValidator urlSecurity, SecretCrypto crypto, ObjectMapper mapper,
                           MonitoringScheduler scheduler) {
        this.endpoints = endpoints;
        this.users = users;
        this.checks = checks;
        this.urlSecurity = urlSecurity;
        this.crypto = crypto;
        this.mapper = mapper;
        this.scheduler = scheduler;
    }

    @Transactional
    public EndpointDtos.EndpointResponse create(UUID ownerId, EndpointDtos.CreateEndpointRequest request) {
        HeaderValidator.validate(request.headers());
        UrlSecurityValidator.ValidatedTarget target = validateTarget(request.url());
        AppUser owner = users.findById(ownerId).orElseThrow(() -> ApiException.notFound("Account"));
        MonitoredEndpoint endpoint = new MonitoredEndpoint(owner, request.name().trim(), target.host(),
            crypto.encrypt(request.url()), encryptHeaders(request.headers()), request.method().toUpperCase(java.util.Locale.ROOT),
            statusCodes(request.expectedStatusCodes()), request.timeoutMs(), request.intervalSeconds(), request.retryCount(),
            request.retryBackoffMs(), request.failureThreshold(), request.recoveryThreshold());
        return toResponse(endpoints.save(endpoint), null);
    }

    @Transactional(readOnly = true)
    public EndpointDtos.EndpointPage list(UUID ownerId, String query, int page, int size, String sortBy, String direction) {
        Pageable pageable = pageable(page, size, sortBy, direction);
        String cleanedQuery = query == null || query.isBlank() ? null : query.trim();
        if (cleanedQuery != null && cleanedQuery.length() > 100) {
            throw ApiException.badRequest("INVALID_SEARCH", "Search query must be at most 100 characters");
        }
        Page<MonitoredEndpoint> rows = cleanedQuery == null
            ? endpoints.findOwned(ownerId, pageable)
            : endpoints.searchOwned(ownerId, cleanedQuery, pageable);
        Map<UUID, HealthCheckRepository.LatestCheckView> latest = latestFor(rows.getContent());
        return new EndpointDtos.EndpointPage(rows.getContent().stream()
            .map(e -> toResponse(e, latest.get(e.getId()))).toList(), rows.getNumber(), rows.getSize(),
            rows.getTotalElements(), rows.getTotalPages(), rows.hasNext());
    }

    @Transactional(readOnly = true)
    public EndpointDtos.EndpointResponse get(UUID ownerId, UUID endpointId) {
        MonitoredEndpoint endpoint = getOwned(ownerId, endpointId);
        return toResponse(endpoint, latestFor(List.of(endpoint)).get(endpointId));
    }

    @Transactional
    public EndpointDtos.EndpointResponse update(UUID ownerId, UUID endpointId, EndpointDtos.UpdateEndpointRequest request) {
        MonitoredEndpoint endpoint = getOwned(ownerId, endpointId);
        if (request.name() != null) {
            if (request.name().isBlank()) throw ApiException.badRequest("INVALID_NAME", "Endpoint name cannot be blank");
            endpoint.setName(request.name().trim());
        }
        if (request.url() != null && !request.url().isBlank()) {
            UrlSecurityValidator.ValidatedTarget target = validateTarget(request.url());
            endpoint.setUrlCiphertext(crypto.encrypt(request.url()));
            endpoint.setUrlHost(target.host());
        }
        if (request.method() != null) endpoint.setMethod(request.method().toUpperCase(java.util.Locale.ROOT));
        if (request.expectedStatusCodes() != null) endpoint.setExpectedStatusCodes(statusCodes(request.expectedStatusCodes()));
        if (request.timeoutMs() != null) endpoint.setTimeoutMs(request.timeoutMs());
        if (request.intervalSeconds() != null) endpoint.setIntervalSeconds(request.intervalSeconds());
        if (request.retryCount() != null) endpoint.setRetryCount(request.retryCount());
        if (request.retryBackoffMs() != null) endpoint.setRetryBackoffMs(request.retryBackoffMs());
        if (request.failureThreshold() != null) endpoint.setFailureThreshold(request.failureThreshold());
        if (request.recoveryThreshold() != null) endpoint.setRecoveryThreshold(request.recoveryThreshold());
        if (request.headers() != null) {
            HeaderValidator.validate(request.headers());
            endpoint.setHeadersCiphertext(encryptHeaders(request.headers()));
        }
        endpoint.setNextCheckAt(Instant.now());
        MonitoredEndpoint saved = endpoints.save(endpoint);
        return toResponse(saved, latestFor(List.of(saved)).get(saved.getId()));
    }

    @Transactional
    public EndpointDtos.EndpointResponse setEnabled(UUID ownerId, UUID endpointId, boolean enabled) {
        MonitoredEndpoint endpoint = getOwned(ownerId, endpointId);
        endpoint.setEnabled(enabled);
        if (enabled) endpoint.setNextCheckAt(Instant.now());
        MonitoredEndpoint saved = endpoints.save(endpoint);
        return toResponse(saved, latestFor(List.of(saved)).get(saved.getId()));
    }

    @Transactional
    public void archive(UUID ownerId, UUID endpointId) {
        MonitoredEndpoint endpoint = getOwned(ownerId, endpointId);
        endpoint.setEnabled(false);
        endpoint.setArchivedAt(Instant.now());
        endpoints.save(endpoint);
    }

    @Transactional(readOnly = true)
    public EndpointDtos.HealthCheckPage checks(UUID ownerId, UUID endpointId, String range, int page, int size) {
        getOwned(ownerId, endpointId); // Ownership is checked before querying any history.
        Instant from = TimeRanges.from(range, Instant.now());
        Pageable pageable = PageRequest.of(validatePage(page), validateSize(size), Sort.by(Sort.Direction.DESC, "checkedAt"));
        Page<HealthCheck> rows = checks.findByEndpoint_IdAndCheckedAtAfterOrderByCheckedAtDesc(endpointId, from, pageable);
        return new EndpointDtos.HealthCheckPage(rows.getContent().stream().map(check ->
            new EndpointDtos.HealthCheckResponse(check.getId(), check.getCheckedAt(), check.getResponseTimeMs(),
                check.getStatusCode(), check.isSuccessful(), check.getFailureDetail(), check.getAttemptCount())).toList(),
            rows.getNumber(), rows.getSize(), rows.getTotalElements(), rows.getTotalPages(), rows.hasNext());
    }

    @Transactional(readOnly = true)
    public MonitoredEndpoint getOwned(UUID ownerId, UUID endpointId) {
        return endpoints.findByIdAndOwner_IdAndArchivedAtIsNull(endpointId, ownerId)
            .orElseThrow(() -> ApiException.notFound("Endpoint"));
    }

    public void requestCheck(UUID ownerId, UUID endpointId) {
        MonitoredEndpoint endpoint = getOwned(ownerId, endpointId);
        if (!endpoint.isEnabled()) throw ApiException.conflict("ENDPOINT_PAUSED", "Resume this endpoint before running a check");
        if (!scheduler.requestManual(endpointId)) throw ApiException.conflict("CHECK_IN_PROGRESS", "A check is already running for this endpoint");
    }

    private UrlSecurityValidator.ValidatedTarget validateTarget(String rawUrl) {
        try {
            return urlSecurity.validate(rawUrl, false);
        } catch (java.io.IOException ex) {
            throw ApiException.badRequest("UNSAFE_TARGET", "Target URL is invalid, unreachable, or blocked by outbound network policy");
        }
    }

    private String encryptHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return null;
        try {
            return crypto.encrypt(mapper.writeValueAsString(headers));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not protect endpoint request headers");
        }
    }

    private List<String> headerNames(MonitoredEndpoint endpoint) {
        if (endpoint.getHeadersCiphertext() == null) return List.of();
        try {
            return mapper.readValue(crypto.decrypt(endpoint.getHeadersCiphertext()), HEADER_MAP).keySet().stream().sorted().toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Stored request configuration is invalid");
        }
    }

    public Map<String, String> decryptHeaders(MonitoredEndpoint endpoint) {
        if (endpoint.getHeadersCiphertext() == null) return Map.of();
        try {
            Map<String, String> result = mapper.readValue(crypto.decrypt(endpoint.getHeadersCiphertext()), HEADER_MAP);
            return result == null ? Map.of() : Map.copyOf(result);
        } catch (IOException ex) {
            throw new IllegalStateException("Stored request configuration is invalid");
        }
    }

    private EndpointDtos.EndpointResponse toResponse(MonitoredEndpoint endpoint, HealthCheckRepository.LatestCheckView latest) {
        String status = healthStatus(endpoint, latest);
        String display = safeDisplay(crypto.decrypt(endpoint.getUrlCiphertext()));
        Instant checkedAt = latest == null ? null : latest.getCheckedAt();
        return new EndpointDtos.EndpointResponse(endpoint.getId(), endpoint.getName(), display,
            hasPath(crypto.decrypt(endpoint.getUrlCiphertext())), endpoint.getMethod(),
            parseStatusCodes(endpoint.getExpectedStatusCodes()), endpoint.getTimeoutMs(), endpoint.getIntervalSeconds(),
            endpoint.getRetryCount(), endpoint.getRetryBackoffMs(), endpoint.getFailureThreshold(), endpoint.getRecoveryThreshold(),
            headerNames(endpoint), endpoint.isEnabled(), status,
            latest == null ? null : latest.getResponseTimeMs(), latest == null ? null : latest.getStatusCode(),
            latest == null ? null : latest.getFailureDetail(), checkedAt, endpoint.getConsecutiveFailures(),
            endpoint.getCreatedAt(), endpoint.getUpdatedAt());
    }

    private Map<UUID, HealthCheckRepository.LatestCheckView> latestFor(List<MonitoredEndpoint> list) {
        if (list.isEmpty()) return Map.of();
        List<UUID> ids = list.stream().map(MonitoredEndpoint::getId).toList();
        return checks.findLatestForEndpoints(ids).stream().collect(Collectors.toMap(
            HealthCheckRepository.LatestCheckView::getEndpointId, item -> item, (a, b) -> a, LinkedHashMap::new));
    }

    private static String healthStatus(MonitoredEndpoint endpoint, HealthCheckRepository.LatestCheckView latest) {
        if (!endpoint.isEnabled()) return "PAUSED";
        if (latest == null || latest.getCheckedAt() == null) return "UNKNOWN";
        Instant checkedAt = latest.getCheckedAt();
        if (checkedAt.isBefore(Instant.now().minus(Math.max(60L, endpoint.getIntervalSeconds() * 3L), ChronoUnit.SECONDS))) return "UNKNOWN";
        return Boolean.TRUE.equals(latest.getSuccessful()) ? "UP" : "DOWN";
    }

    private static String safeDisplay(String rawUrl) {
        URI uri = URI.create(rawUrl);
        String authority = uri.getRawAuthority(); // userinfo is rejected before storage; query and path never leave storage.
        return uri.getScheme().toLowerCase(java.util.Locale.ROOT) + "://" + authority + (hasPath(uri) ? "/…" : "");
    }

    private static boolean hasPath(String rawUrl) { return hasPath(URI.create(rawUrl)); }
    private static boolean hasPath(URI uri) {
        String path = uri.getRawPath();
        return path != null && !path.isBlank() && !path.equals("/");
    }

    private static String statusCodes(List<Integer> values) {
        if (values == null || values.isEmpty()) throw ApiException.badRequest("INVALID_STATUS_CODES", "Select at least one expected HTTP status code");
        if (values.stream().anyMatch(value -> value == null || value < 100 || value > 599)) {
            throw ApiException.badRequest("INVALID_STATUS_CODES", "Expected HTTP status codes must be between 100 and 599");
        }
        Set<Integer> unique = new LinkedHashSet<>(values);
        if (unique.size() != values.size()) throw ApiException.badRequest("INVALID_STATUS_CODES", "Expected HTTP status codes cannot contain duplicates");
        return unique.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static List<Integer> parseStatusCodes(String codes) {
        if (codes == null || codes.isBlank()) return new ArrayList<>();
        return java.util.Arrays.stream(codes.split(",")).map(Integer::parseInt).toList();
    }

    private static Pageable pageable(int page, int size, String sortBy, String direction) {
        int safePage = validatePage(page);
        int safeSize = validateSize(size);
        String property = switch (sortBy == null ? "createdAt" : sortBy) {
            case "name" -> "name";
            case "createdAt" -> "createdAt";
            case "intervalSeconds" -> "intervalSeconds";
            default -> throw ApiException.badRequest("INVALID_SORT", "Sort by name, createdAt, or intervalSeconds");
        };
        Sort.Direction dir;
        try { dir = Sort.Direction.fromString(direction == null ? "desc" : direction); }
        catch (IllegalArgumentException ex) { throw ApiException.badRequest("INVALID_SORT", "Sort direction must be asc or desc"); }
        return PageRequest.of(safePage, safeSize, Sort.by(dir, property));
    }

    private static int validatePage(int page) {
        if (page < 0 || page > 100_000) throw ApiException.badRequest("INVALID_PAGE", "Page must be between 0 and 100000");
        return page;
    }

    private static int validateSize(int size) {
        if (size < 1 || size > 100) throw ApiException.badRequest("INVALID_SIZE", "Page size must be between 1 and 100");
        return size;
    }
}
