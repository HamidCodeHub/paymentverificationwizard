package com.pantea.receiptgate.web;

import com.pantea.receiptgate.config.GateProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shared-secret check on /api/*. Left empty in config the filter is a no-op, which is fine locally but
 * must never be the case on a public server (the prod profile refuses to start without it).
 * Only GET /api/v1/health is public.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    private final GateProperties props;

    public ApiKeyFilter(GateProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String expected = props.getApiKey();
        if (expected == null || expected.isBlank() || isPublic(request)) {
            chain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader("X-Api-Key");
        if (provided == null || !constantTimeEquals(expected, provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or missing API key\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isPublic(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().endsWith("/api/v1/health");
    }

    /** Avoids leaking key length or prefix through response timing. */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
