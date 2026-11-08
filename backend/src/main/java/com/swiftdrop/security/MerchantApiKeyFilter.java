package com.swiftdrop.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.exception.ApiError;
import com.swiftdrop.exception.InvalidApiKeyException;
import com.swiftdrop.exception.MerchantNotApprovedException;
import com.swiftdrop.exception.RateLimitException;
import com.swiftdrop.service.merchant.MerchantApiKeyAuthenticator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class MerchantApiKeyFilter extends OncePerRequestFilter {
    static final String HEADER = "X-API-Key";
    static final int REQUEST_LIMIT_PER_MINUTE = 300;
    static final int FAILURE_LIMIT_PER_MINUTE = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final MerchantApiKeyAuthenticator authenticator;
    private final AuthRateLimiter limiter;
    private final ObjectMapper json;

    public MerchantApiKeyFilter(MerchantApiKeyAuthenticator authenticator, AuthRateLimiter limiter, ObjectMapper json) {
        this.authenticator = authenticator;
        this.limiter = limiter;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        var source = request.getRemoteAddr();
        try {
            limiter.requireAllowed("merchant-api:" + source, REQUEST_LIMIT_PER_MINUTE, WINDOW);
        } catch (RateLimitException exception) {
            throttled(response, exception);
            return;
        }
        try {
            var principal = authenticator.authenticate(request.getHeader(HEADER));
            var authorities = principal.scopes().stream()
                    .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).toList();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
            SecurityContextHolder.setContext(context);
        } catch (InvalidApiKeyException invalid) {
            try {
                limiter.requireAllowed("merchant-api-fail:" + source, FAILURE_LIMIT_PER_MINUTE, WINDOW);
            } catch (RateLimitException exception) {
                throttled(response, exception);
                return;
            }
            write(response, 401, ApiError.of("INVALID_API_KEY", "Invalid or revoked API key."));
            return;
        } catch (MerchantNotApprovedException notApproved) {
            write(response, 403, ApiError.of("MERCHANT_NOT_APPROVED", "This business is not approved."));
            return;
        }
        chain.doFilter(request, response);
    }

    private void throttled(HttpServletResponse response, RateLimitException exception) throws IOException {
        response.setHeader("Retry-After", Long.toString(exception.retryAfter()));
        write(response, 429, ApiError.of("RATE_LIMITED", "Too many requests. Please try again later."));
    }

    private void write(HttpServletResponse response, int status, ApiError body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getWriter(), body);
    }
}