package dev.apisentinel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryingCheckRunnerTest {
    @Test
    void retriesNetworkFailuresWithExponentialBackoffAndStopsOnSuccess() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        RetryingCheckRunner runner = new RetryingCheckRunner(delays::add);
        var result = runner.run(() -> {
            if (calls.incrementAndGet() == 1) throw new IOException("secret URL must not escape");
            return 204;
        }, Set.of(200, 204), 3, 150, 5000);

        assertTrue(result.successful());
        assertEquals(204, result.statusCode());
        assertEquals(2, result.attempts());
        assertEquals(List.of(150L), delays);
        assertEquals(2, calls.get());
    }

    @Test
    void enforcesTheRetryLimitAndUsesCappedBackoff() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        RetryingCheckRunner runner = new RetryingCheckRunner(delays::add);
        var result = runner.run(() -> {
            calls.incrementAndGet();
            return 503;
        }, Set.of(200), 3, 2000, 1000);

        assertFalse(result.successful());
        assertEquals(4, calls.get());
        assertEquals(4, result.attempts());
        assertEquals(List.of(2000L, 4000L, 8000L), delays);
        assertEquals("Unexpected HTTP status 503", result.failureDetail());
    }

    @Test
    void doesNotRetryAnExplicitlyBlockedDestination() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        RetryingCheckRunner runner = new RetryingCheckRunner(delays::add);
        var result = runner.run(() -> {
            calls.incrementAndGet();
            throw new BlockedTargetException();
        }, Set.of(200), 3, 100, 5000);

        assertFalse(result.successful());
        assertEquals(1, result.attempts());
        assertTrue(delays.isEmpty());
        assertEquals("Target blocked by SSRF policy", result.failureDetail());
    }
}
