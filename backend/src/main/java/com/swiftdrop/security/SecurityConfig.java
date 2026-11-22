package com.swiftdrop.security;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.config.AuthSettings;
import com.swiftdrop.exception.ApiError;
import com.swiftdrop.integration.identity.GoogleCallbackHandler;
import com.swiftdrop.integration.identity.GoogleFailureHandler;

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
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/auth/csrf", "/api/v1/auth/providers").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout",
                                "/api/v1/auth/password-reset/request", "/api/v1/auth/password-reset/confirm",
                                "/api/v1/auth/email-verification/request", "/api/v1/auth/email-verification/confirm", "/api/v1/auth/email-change/confirm").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/me").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/me/google-link", "/api/v1/me/security/email-change",
                                "/api/v1/me/security/password", "/api/v1/me/security/google-reauth").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/me/addresses").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/me/addresses").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/me/addresses/{id}").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/me/addresses/{id}").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/roles/grant", "/api/v1/admin/users/*/roles/revoke").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/courier-role").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/operational-roles").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users/*/roles").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users", "/api/v1/admin/users/*").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/suspend", "/api/v1/admin/users/*/reactivate").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/merchants").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/merchants/*/approve", "/api/v1/admin/merchants/*/reject")
                            .hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/merchants/owners/*/suspend")
                            .hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/bookings/dashboard").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/bookings", "/api/v1/bookings/{bookingId}").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/bookings").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/bookings/*/receipt").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET,"/api/v1/merchant/dashboard/orders", "/api/v1/merchant/dashboard/orders/{id}").hasRole("MERCHANT")
                        .requestMatchers(HttpMethod.POST,"/api/v1/merchant/dashboard/orders", "/api/v1/merchant/dashboard/orders/quote").hasRole("MERCHANT")
                        .requestMatchers(HttpMethod.GET,"/api/v1/admin/notifications/templates","/api/v1/admin/notifications/jobs","/api/v1/admin/notifications/jobs/*/attempts").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.PUT,"/api/v1/admin/notifications/templates/*").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/admin/notifications/jobs/*/retry").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/admin/integrations/queues").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/admin/integrations/*/replay").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/tracking/{trackingNumber}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tracking/*/events").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tracking/customer/{trackingNumber}").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/merchant/registration").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/merchant/registration").hasAnyRole("CUSTOMER", "MERCHANT")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/merchant/registration").hasAnyRole("CUSTOMER", "MERCHANT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/merchant/api-keys").hasRole("MERCHANT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/merchant/api-keys", "/api/v1/merchant/api-keys/*/revoke").hasRole("MERCHANT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/merchant/webhooks", "/api/v1/merchant/webhooks/*/deliveries").hasRole("MERCHANT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/merchant/webhooks/*/deliveries/*/attempts").hasRole("MERCHANT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/merchant/webhooks/*/deliveries/*/retry").hasRole("MERCHANT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/merchant/webhooks", "/api/v1/merchant/webhooks/*/rotate-secret",
                                "/api/v1/merchant/webhooks/*/test").hasRole("MERCHANT")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/merchant/webhooks/*").hasRole("MERCHANT")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/merchant/webhooks/*").hasRole("MERCHANT")
                        .requestMatchers(HttpMethod.GET,"/api/v1/courier/deliveries","/api/v1/courier/deliveries/{id}").hasRole("COURIER")
                        .requestMatchers(HttpMethod.POST,"/api/v1/courier/deliveries/{id}/status").hasRole("COURIER")
                        .requestMatchers(HttpMethod.GET,"/api/v1/dispatch/couriers","/api/v1/dispatch/couriers/active","/api/v1/dispatch/couriers/{id}/workload",
                                "/api/v1/dispatch/bookings","/api/v1/dispatch/bookings/{id}","/api/v1/dispatch/bookings/{id}/assignments","/api/v1/dispatch/reports/daily").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.PUT,"/api/v1/dispatch/couriers/{id}").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/dispatch/assignments","/api/v1/dispatch/assignments/reassign").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/quotes").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/quotes/*").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/zones", "/api/v1/admin/rate-cards").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/zones", "/api/v1/admin/zones/*/postcodes",
                                "/api/v1/admin/rate-cards").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/postcodes").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/postcodes/*").hasRole("DISPATCHER_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/zones/*", "/api/v1/admin/rate-cards/*").hasRole("DISPATCHER_ADMIN")
                        .anyRequest().denyAll())
                .csrf(config -> config.csrfTokenRepository(csrf)
                        // Resource-server defaults otherwise exempt Bearer requests from CSRF.
                        // Browser mutations follow the same CSRF contract as cookie auth operations.
                        .withObjectPostProcessor(new org.springframework.security.config.ObjectPostProcessor<Object>() {
                            @Override public <O> O postProcess(O object) {
                                if (object instanceof CsrfFilter filter) {
                                    filter.setRequireCsrfProtectionMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER);
                                }
                                return object;
                            }
                        }))
                .addFilterBefore(new AuthRequestFilter(settings, limiter, json, loginLimit), CsrfFilter.class)
                .addFilterBefore(new TrackingRateLimitFilter(limiter, json), CsrfFilter.class)
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
            com.swiftdrop.config.GoogleSettings googleSettings,
            org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clients) throws Exception {
        return http
                .securityMatcher("/oauth2/**", "/login/oauth2/**")
                .addFilterBefore(new GoogleOriginFilter(googleSettings),
                        org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter.class)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(new com.swiftdrop.integration.identity.GoogleSecurityRequestResolver(clients)))
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
