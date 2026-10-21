package com.pantea.receiptgate.web;

import com.pantea.receiptgate.config.GateProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window per-IP throttle on the check endpoint. A check costs up to three Tesseract runs, so an
 * unthrottled endpoint is an easy way to pin the CPU.
 *
 * <p>All traffic arrives from the WordPress server, so the meaningful IP is the customer's: have the plugin
 * send it as X-Forwarded-For, and keep the limit generous enough for one shop's traffic.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final GateProperties props;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(GateProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        int limit = props.getRateLimitPerMinute();
        if (limit <= 0) {
            chain.doFilter(request, response);
            return;
        }

        long minute = Instant.now().getEpochSecond() / 60;
        Window window = windows.compute(clientIp(request), (k, existing) ->
                existing == null || existing.minute != minute ? new Window(minute) : existing);

        if (window.count.incrementAndGet() > limit) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", "60");
            response.getWriter().write("{\"error\":\"Too many uploads. Please wait a minute and try again.\"}");
            return;
        }

        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute != minute);
        }
        chain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        final long minute;
        final AtomicInteger count = new AtomicInteger();

        Window(long minute) {
            this.minute = minute;
        }
    }
}
