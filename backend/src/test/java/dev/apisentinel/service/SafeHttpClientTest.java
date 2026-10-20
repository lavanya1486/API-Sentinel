package dev.apisentinel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.apisentinel.config.AppProperties;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SafeHttpClientTest {
    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception { server = new MockWebServer(); server.start(); }

    @AfterEach
    void stopServer() throws Exception { server.shutdown(); }

    @Test
    void usesControlledLocalServerAndDoesNotFollowRedirects() throws Exception {
        String target = server.url("/health").toString();
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/redirected")
            .setBody("this response body is discarded"));
        UrlSecurityValidator validator = Mockito.mock(UrlSecurityValidator.class);
        when(validator.validate(target, false)).thenReturn(new UrlSecurityValidator.ValidatedTarget(URI.create(target), "localhost", true));
        when(validator.resolvePublicAddresses(anyString())).thenReturn(List.of(InetAddress.getLoopbackAddress()));
        SafeHttpClient client = new SafeHttpClient(validator, properties());

        SafeHttpClient.HttpResult result = client.check(target, "GET", Map.of(), 2000);

        assertEquals(302, result.statusCode());
        assertEquals(1, server.getRequestCount(), "the redirect target must never be requested");
    }

    @Test
    void reportsSuccessfulChecksAndEnforcesThePerRequestTimeout() throws Exception {
        String target = server.url("/status").toString();
        UrlSecurityValidator validator = Mockito.mock(UrlSecurityValidator.class);
        when(validator.validate(target, false)).thenReturn(new UrlSecurityValidator.ValidatedTarget(URI.create(target), "localhost", true));
        when(validator.resolvePublicAddresses(anyString())).thenReturn(List.of(InetAddress.getLoopbackAddress()));
        SafeHttpClient client = new SafeHttpClient(validator, properties());

        server.enqueue(new MockResponse().setResponseCode(204));
        assertEquals(204, client.check(target, "GET", Map.of(), 2000).statusCode());

        server.enqueue(new MockResponse().setHeadersDelay(2, TimeUnit.SECONDS).setResponseCode(200));
        assertThrows(IOException.class, () -> client.check(target, "GET", Map.of(), 500));
    }

    private static AppProperties properties() {
        return new AppProperties(new AppProperties.Security("unit-test-jwt-secret-long-enough-for-hmac", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", 15, false),
            new AppProperties.Cors("http://localhost:5173"), new AppProperties.Monitoring(5000, 8, 90, 65536),
            new AppProperties.Notifications(5, 5));
    }
}
