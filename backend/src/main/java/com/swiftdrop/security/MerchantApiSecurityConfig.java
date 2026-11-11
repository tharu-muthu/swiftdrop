package com.swiftdrop.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.exception.ApiError;
import com.swiftdrop.service.merchant.MerchantApiKeyAuthenticator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/** Partner API: authenticated by merchant API key, separate from the browser/JWT chain. */
@Configuration
public class MerchantApiSecurityConfig {
    @Bean @Order(0)
    SecurityFilterChain merchantApiSecurity(HttpSecurity http, ObjectMapper json,
            MerchantApiKeyAuthenticator authenticator, AuthRateLimiter limiter) throws Exception {
        return http
                .securityMatcher("/api/v1/merchant/verify",
                        "/api/v1/merchant/quotes", "/api/v1/merchant/quotes/**",
                        "/api/v1/merchant/orders", "/api/v1/merchant/orders/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/v1/merchant/verify").authenticated()
                        .requestMatchers("/api/v1/merchant/quotes", "/api/v1/merchant/quotes/**").hasAuthority("SCOPE_quote")
                        .requestMatchers("/api/v1/merchant/orders", "/api/v1/merchant/orders/**").hasAuthority("SCOPE_order")
                        .anyRequest().denyAll())
                // Server-to-server and key-authenticated: no cookies or sessions, so there is nothing for CSRF to protect.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(new MerchantApiKeyFilter(authenticator, limiter, json), AuthorizationFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(unauthorized(json))
                        .accessDeniedHandler(forbidden(json)))
                .build();
    }

    private AuthenticationEntryPoint unauthorized(ObjectMapper json) {
        return (request, response, exception) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getWriter(), ApiError.of("INVALID_API_KEY", "Invalid or revoked API key."));
        };
    }

    private AccessDeniedHandler forbidden(ObjectMapper json) {
        return (request, response, exception) -> {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getWriter(), ApiError.of("FORBIDDEN", "This API key is not allowed to do that."));
        };
    }
}