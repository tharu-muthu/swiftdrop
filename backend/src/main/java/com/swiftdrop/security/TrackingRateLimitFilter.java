package com.swiftdrop.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.exception.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import org.springframework.web.filter.OncePerRequestFilter;

public class TrackingRateLimitFilter extends OncePerRequestFilter {
    private final AuthRateLimiter limiter; private final ObjectMapper json;
    public TrackingRateLimitFilter(AuthRateLimiter limiter,ObjectMapper json) { this.limiter=limiter; this.json=json; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if(request.getServletPath().startsWith("/api/v1/tracking/")) {
            try { limiter.requireAllowed("tracking:"+request.getRemoteAddr(),120,Duration.ofMinutes(1)); }
            catch(RateLimitException limited) {
                response.setStatus(429); response.setHeader("Retry-After",Long.toString(limited.retryAfter())); response.setContentType("application/json");
                json.writeValue(response.getWriter(),ApiError.of("RATE_LIMITED","Too many tracking requests. Try again shortly.")); return;
            }
        }
        chain.doFilter(request,response);
    }
}
