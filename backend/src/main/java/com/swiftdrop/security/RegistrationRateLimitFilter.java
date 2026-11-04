package com.swiftdrop.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Bounded single-instance abuse protection; never trusts client-supplied forwarding headers. */
@Component
public class RegistrationRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MS = 600_000;
    private final Map<String, Window> windows = new HashMap<>();
    private final ObjectMapper json;
    private final int limit;
    public RegistrationRateLimitFilter(ObjectMapper json,
            @Value("${swiftdrop.registration.requests-per-window:20}") int limit) {
        this.json = json;
        this.limit = limit;
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getMethod().equals("POST") || !request.getServletPath().equals("/api/v1/auth/register");
    }
    private synchronized boolean allow(String address) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> entry.getValue().expires <= now);
        var window = windows.get(address);
        if (window == null) {
            if (windows.size() >= 10_000) return false;
            window = new Window(now + WINDOW_MS);
            windows.put(address, window);
        }
        return ++window.count <= limit;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        if (!allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "600");
            response.setContentType("application/json");
            json.writeValue(response.getWriter(), ApiError.of("RATE_LIMITED", "Too many attempts. Please try again later."));
            return;
        }
        chain.doFilter(request, response);
    }
    private static class Window {
        private final long expires;
        private int count;
        Window(long expires) { this.expires = expires; }
    }
}
