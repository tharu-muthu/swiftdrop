package com.swiftdrop.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.config.AuthSettings;
import com.swiftdrop.exception.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;

public class AuthRequestFilter extends OncePerRequestFilter {
    private final AuthSettings settings;
    private final AuthRateLimiter limiter;
    private final ObjectMapper json;
    private final Map<String, Integer> limits;
    public AuthRequestFilter(AuthSettings settings, AuthRateLimiter limiter, ObjectMapper json, int loginLimit) {
        this.settings = settings; this.limiter = limiter; this.json = json;
        this.limits = Map.of("login", loginLimit, "refresh", 60, "logout", 60, "csrf", 120,
                "password-reset/request", 10, "password-reset/confirm", 20,
                "email-verification/request", 10, "email-verification/confirm", 120);
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/v1/auth/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        String origin = request.getHeader("Origin");
        boolean unsafe = !request.getMethod().equals("GET") && !request.getMethod().equals("HEAD");
        if ("cross-site".equals(request.getHeader("Sec-Fetch-Site"))
                || (origin != null && !settings.allowedOrigins().contains(origin)) || (unsafe && origin == null)) {
            response.setStatus(403); response.setContentType("application/json");
            json.writeValue(response.getWriter(), ApiError.of("FORBIDDEN", "Request origin is not allowed.")); return;
        }
        String endpoint = request.getServletPath().substring("/api/v1/auth/".length());
        if (limits.containsKey(endpoint)) {
            try { limiter.requireAllowed(endpoint + ":" + request.getRemoteAddr(), limits.get(endpoint), Duration.ofMinutes(1)); }
            catch (RateLimitException exception) {
                response.setStatus(429); response.setHeader("Retry-After", Long.toString(exception.retryAfter()));
                response.setContentType("application/json");
                json.writeValue(response.getWriter(), ApiError.of("RATE_LIMITED", "Too many attempts. Please try again later.")); return;
            }
        }
        chain.doFilter(request, response);
    }
}
