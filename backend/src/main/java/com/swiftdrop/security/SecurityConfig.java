package com.swiftdrop.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.config.AuthSettings;
import com.swiftdrop.exception.ApiError;
import com.swiftdrop.integration.identity.GoogleCallbackHandler;
import com.swiftdrop.integration.identity.GoogleFailureHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration
public class SecurityConfig {
    private AuthenticationEntryPoint unauthorized(ObjectMapper json) {
        return (request, response, exception) -> {
            response.setStatus(401); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getWriter(), ApiError.of("UNAUTHORIZED", "Authentication required"));
        };
    }
    private AccessDeniedHandler forbidden(ObjectMapper json) {
        return (request, response, exception) -> {
            response.setStatus(403); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getWriter(), ApiError.of("FORBIDDEN", "Access denied"));
        };
    }
    @Bean @Order(1)
    SecurityFilterChain apiSecurity(HttpSecurity http, ObjectMapper json, AuthSettings settings,
            JwtDecoder decoder, IdentityJwtConverter converter, AuthRateLimiter limiter,
            @Value("${swiftdrop.auth.source-login-limit:10}") int loginLimit) throws Exception {
        var csrf = new CookieCsrfTokenRepository();
        csrf.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(!settings.localhostDevelopment()).sameSite("Lax").path("/"));
        var bearer = new DefaultBearerTokenResolver();
        return http.securityMatcher("/api/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/bookings/dashboard").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tracking/{trackingNumber}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tracking/*/events").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tracking/customer/{trackingNumber}").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/merchant/registration").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/merchant/registration").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/merchant/api-keys").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/merchant/api-keys", "/api/v1/merchant/api-keys/*/revoke").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/merchant/webhooks", "/api/v1/merchant/webhooks/*/deliveries").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/merchant/webhooks", "/api/v1/merchant/webhooks/*/rotate-secret",
                                "/api/v1/merchant/webhooks/*/test").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/merchant/webhooks/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/merchant/webhooks/*").authenticated()
                        .anyRequest().denyAll())
                .csrf(config -> config.csrfTokenRepository(csrf))
                .addFilterBefore(new AuthRequestFilter(settings, limiter, json, loginLimit), CsrfFilter.class)
                // API identities live only in this request. Existing OAuth framework sessions are left intact.
                .securityContext(context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(request -> request.getServletPath().startsWith("/api/v1/auth/") ? null : bearer.resolve(request))
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(unauthorized(json)).accessDeniedHandler(forbidden(json)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized(json)).accessDeniedHandler(forbidden(json)))
                .build();
    }
    @Bean @Order(2)
    @ConditionalOnProperty(prefix = "swiftdrop.google", name = "enabled", havingValue = "true")
    SecurityFilterChain futureOAuthSecurity(HttpSecurity http, ObjectMapper json,
            GoogleCallbackHandler successHandler, GoogleFailureHandler failureHandler,
            com.swiftdrop.config.GoogleSettings googleSettings) throws Exception {
        return http
                .securityMatcher("/oauth2/**", "/login/oauth2/**")
                .addFilterBefore(new GoogleOriginFilter(googleSettings),
                        org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter.class)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .oauth2Login(oauth -> oauth
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(unauthorized(json))
                        .accessDeniedHandler(forbidden(json)))
                .build();
    }

    @Bean @Order(3)
    SecurityFilterChain denyUnmatchedRequests(HttpSecurity http, ObjectMapper json) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll())
                .csrf(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(unauthorized(json))
                        .accessDeniedHandler(forbidden(json)))
                .build();
    }
}
