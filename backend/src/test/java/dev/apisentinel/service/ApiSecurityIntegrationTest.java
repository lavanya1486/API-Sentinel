package dev.apisentinel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.apisentinel.dto.AuthDtos;
import dev.apisentinel.dto.EndpointDtos;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApiSecurityIntegrationTest.SafeDnsConfiguration.class)
class ApiSecurityIntegrationTest {
    private static final String ENCRYPTION_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.jwt-secret", () -> "integration-test-jwt-secret-at-least-thirty-two-bytes");
        registry.add("app.security.encryption-key", () -> ENCRYPTION_KEY);
        registry.add("app.security.trust-proxy-headers", () -> false);
        registry.add("app.monitoring.scan-delay-ms", () -> 3_600_000);
        registry.add("app.monitoring.history-retention-days", () -> 90);
        registry.add("app.notifications.scan-delay-ms", () -> 3_600_000);
    }

    @Autowired TestRestTemplate rest;
    @LocalServerPort int port;

    @Test
    void registrationPersistsAndEndpointResourcesAreIsolatedByOwner() {
        AuthDtos.AuthResponse owner = register("owner-" + System.nanoTime() + "@example.net");
        AuthDtos.AuthResponse another = register("another-" + System.nanoTime() + "@example.net");
        EndpointDtos.CreateEndpointRequest request = new EndpointDtos.CreateEndpointRequest(
            "Payments API", "https://api.testhost/health?token=must-not-be-returned", "GET", List.of(200),
            5000, 60, 1, 200, 2, 2, Map.of("Authorization", "Bearer integration-secret"));
        ResponseEntity<EndpointDtos.EndpointResponse> created = rest.exchange(base() + "/api/v1/endpoints",
            HttpMethod.POST, authorized(owner.token(), request), EndpointDtos.EndpointResponse.class);

        assertEquals(201, created.getStatusCode().value());
        EndpointDtos.EndpointResponse endpoint = created.getBody();
        assertNotNull(endpoint);
        assertTrue(endpoint.headerNames().contains("Authorization"));
        assertFalse(endpoint.targetDisplay().contains("must-not-be-returned"));
        assertFalse(endpoint.toString().contains("integration-secret"));

        ResponseEntity<String> crossOwner = rest.exchange(base() + "/api/v1/endpoints/" + endpoint.id(),
            HttpMethod.GET, authorized(another.token(), null), String.class);
        assertEquals(404, crossOwner.getStatusCode().value());

        ResponseEntity<EndpointDtos.EndpointPage> ownPage = rest.exchange(base() + "/api/v1/endpoints",
            HttpMethod.GET, authorized(owner.token(), null), EndpointDtos.EndpointPage.class);
        assertEquals(1, ownPage.getBody().totalElements());
        ResponseEntity<EndpointDtos.EndpointPage> anotherPage = rest.exchange(base() + "/api/v1/endpoints",
            HttpMethod.GET, authorized(another.token(), null), EndpointDtos.EndpointPage.class);
        assertEquals(0, anotherPage.getBody().totalElements());
    }

    private AuthDtos.AuthResponse register(String email) {
        ResponseEntity<AuthDtos.AuthResponse> response = rest.postForEntity(base() + "/api/v1/auth/register",
            new AuthDtos.RegisterRequest(email, "strong-password-123"), AuthDtos.AuthResponse.class);
        assertEquals(201, response.getStatusCode().value());
        return response.getBody();
    }

    private HttpEntity<?> authorized(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private String base() { return "http://localhost:" + port; }

    @TestConfiguration
    static class SafeDnsConfiguration {
        @Bean
        @Primary
        HostResolver testHostResolver() {
            return host -> {
                try { return List.of(InetAddress.getByName("8.8.8.8")); }
                catch (UnknownHostException ex) { throw new IllegalStateException(ex); }
            };
        }
    }
}
