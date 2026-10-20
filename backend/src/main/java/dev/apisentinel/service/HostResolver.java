package dev.apisentinel.service;

import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

@FunctionalInterface
public interface HostResolver {
    List<InetAddress> resolve(String host) throws UnknownHostException;

    @Component
    final class SystemResolver implements HostResolver {
        private final ThreadPoolExecutor executor;

        public SystemResolver() {
            ThreadFactory factory = task -> {
                Thread thread = new Thread(task, "sentinel-dns");
                thread.setDaemon(true);
                return thread;
            };
            executor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(128), factory, new ThreadPoolExecutor.AbortPolicy());
        }

        @Override
        public List<InetAddress> resolve(String host) throws UnknownHostException {
            final Future<List<InetAddress>> future;
            try {
                future = executor.submit(() -> Arrays.asList(InetAddress.getAllByName(host)));
            } catch (java.util.concurrent.RejectedExecutionException ex) {
                throw new UnknownHostException("DNS resolver is busy");
            }
            try {
                return future.get(2, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                future.cancel(true);
                throw new UnknownHostException("DNS lookup timed out");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new UnknownHostException("DNS lookup interrupted");
            } catch (ExecutionException ex) {
                UnknownHostException result = new UnknownHostException("DNS lookup failed");
                result.initCause(ex.getCause());
                throw result;
            }
        }

        @PreDestroy
        public void shutdown() { executor.shutdownNow(); }
    }
}
