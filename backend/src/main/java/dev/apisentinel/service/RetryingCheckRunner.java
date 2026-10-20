package dev.apisentinel.service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;

@Component
public class RetryingCheckRunner {
    private final Sleeper sleeper;

    public RetryingCheckRunner() { this(Thread::sleep); }

    public RetryingCheckRunner(Sleeper sleeper) { this.sleeper = sleeper; }

    public Result run(Callable<Integer> request, Set<Integer> expectedStatuses, int maxRetries,
                      int baseBackoffMs, int timeoutMs) {
        long start = System.nanoTime();
        int attempts = 0;
        Integer statusCode = null;
        String failure = "Check failed";
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            attempts++;
            try {
                statusCode = request.call();
                if (expectedStatuses.contains(statusCode)) {
                    return new Result(true, statusCode, elapsed(start), attempts, null);
                }
                failure = "Unexpected HTTP status " + statusCode;
            } catch (Exception ex) {
                statusCode = null;
                failure = safeReason(ex);
                if (ex instanceof BlockedTargetException || ex instanceof BlockedDnsException) break;
            }
            if (attempt < maxRetries) {
                long delay = Math.min(30_000L, Math.max(100L, baseBackoffMs) * (1L << attempt));
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    failure = "Check interrupted";
                    break;
                }
            }
        }
        return new Result(false, statusCode, elapsed(start), attempts, failure);
    }

    private static String safeReason(Exception ex) {
        if (ex instanceof BlockedTargetException || ex instanceof BlockedDnsException) return "Target blocked by SSRF policy";
        if (ex instanceof SocketTimeoutException || ex instanceof java.io.InterruptedIOException) return "Request timed out";
        if (ex instanceof javax.net.ssl.SSLException) return "TLS validation failed";
        if (ex instanceof ConnectException) return "Connection failed";
        if (ex instanceof UnknownHostException) return "DNS lookup failed";
        if (ex instanceof IOException) return "Network request failed";
        return "Check could not be completed";
    }

    private static long elapsed(long start) { return Math.max(0, (System.nanoTime() - start) / 1_000_000L); }

    @FunctionalInterface
    public interface Sleeper { void sleep(long millis) throws InterruptedException; }

    public record Result(boolean successful, Integer statusCode, long responseTimeMs, int attempts, String failureDetail) { }
}
