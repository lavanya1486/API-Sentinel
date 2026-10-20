package dev.apisentinel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.apisentinel.config.AppProperties;
import dev.apisentinel.web.GlobalExceptionHandler.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_BUCKETS = 10_000;
    private static final long WINDOW_MS = 60_000;
    private final ObjectMapper mapper;
    private final boolean trustProxyHeaders;
    private final Map<String, Window> windows = new LinkedHashMap<>(256, 0.75f, true);

    public RateLimitFilter(ObjectMapper mapper, AppProperties properties) {
        this.mapper = mapper;
        this.trustProxyHeaders = properties.security().trustProxyHeaders();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        boolean auth = path.startsWith("/api/v1/auth/");
        String address = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if (trustProxyHeaders) {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && realIp.length() <= 45 && realIp.matches("[0-9a-fA-F:.]+")) address = realIp;
        }
        String key = (auth ? "auth:" : "api:") + address;
        int limit = auth ? 10 : 240;
        long wait = consume(key, limit, System.currentTimeMillis());
        if (wait > 0) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", Long.toString(Math.max(1, (wait + 999) / 1000)));
            mapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), 429, "RATE_LIMITED",
                "Too many requests. Please retry shortly.", path, Map.of()));
            return;
        }
        chain.doFilter(request, response);
    }

    private synchronized long consume(String key, int limit, long now) {
        Window window = windows.get(key);
        if (window == null || now - window.start >= WINDOW_MS) {
            windows.put(key, new Window(now, 1));
            trim(now);
            return 0;
        }
        if (window.count >= limit) return WINDOW_MS - (now - window.start);
        window.count++;
        return 0;
    }

    private void trim(long now) {
        if (windows.size() <= MAX_BUCKETS) return;
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (windows.size() > MAX_BUCKETS && iterator.hasNext()) {
            Map.Entry<String, Window> entry = iterator.next();
            if (now - entry.getValue().start >= WINDOW_MS || windows.size() > MAX_BUCKETS) iterator.remove();
        }
    }

    private static final class Window {
        private final long start;
        private int count;
        private Window(long start, int count) { this.start = start; this.count = count; }
    }
}
