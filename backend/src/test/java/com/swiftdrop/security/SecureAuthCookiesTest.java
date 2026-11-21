package com.swiftdrop.security;

import com.swiftdrop.config.*;
import com.swiftdrop.controller.identity.*;
import com.swiftdrop.dto.identity.*;
import com.swiftdrop.service.identity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpHeaders;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {RegistrationController.class, SessionController.class}, properties = {
        "swiftdrop.google.enabled=false",
        "swiftdrop.auth.localhost-development=false", "swiftdrop.auth.allowed-origins=https://app.test",
        "swiftdrop.auth.source-login-limit=1" })
@Import({SecurityConfig.class, JwtConfig.class, AuthRateLimiter.class})
class SecureAuthCookiesTest {
    @Autowired MockMvc mvc;
    @Autowired Clock clock;
    @MockitoBean RegistrationService registration;
    @MockitoBean IdentitySessionService sessions;
    @MockitoBean IdentityJwtConverter converter;
    @Test void productionCsrfAndRefreshCookiesAreSecureAndLogoutClearsWithMatchingAttributes() throws Exception {
        var csrf = mvc.perform(get("/api/v1/auth/csrf").servletPath("/api/v1/auth/csrf")
                .with(request -> { ((org.springframework.mock.web.MockServletContext) request.getServletContext()).setMajorVersion(6); return request; }))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(csrf.getHeader(HttpHeaders.SET_COOKIE)).contains("Secure", "HttpOnly");
        assertThat(csrf.getCookie("XSRF-TOKEN").getAttribute("SameSite")).isEqualTo("Lax");
        when(sessions.login(any())).thenReturn(new IdentitySessionService.SessionTokens(
                new TokenResponse("synthetic-access", "Bearer", 600), "synthetic-refresh", clock.instant().plusSeconds(604800)));
        var controller = new SessionController(sessions, new AuthSettings("issuer", "api", "", java.time.Duration.ofMinutes(10),
                java.time.Duration.ofDays(7), false, java.util.List.of("https://app.test")), clock);
        var login = controller.login(new LoginRequest("fixture@example.test", "fixture"));
        assertThat(login.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Secure", "HttpOnly", "Path=/api/v1/auth", "SameSite=Lax");
        assertThat(controller.logout("synthetic-refresh").getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("Secure", "HttpOnly", "Path=/api/v1/auth", "SameSite=Lax", "Max-Age=0");
    }
    @Test void loginSourceLimitReturns429AndDoesNotTrustSpoofedForwardedAddresses() throws Exception {
        // Invalid CSRF attempts still consume the source budget; spoofed forwarding headers cannot reset it.
        mvc.perform(post("/api/v1/auth/login").servletPath("/api/v1/auth/login").header("Origin", "https://app.test").header("X-Forwarded-For", "192.0.2.1"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/login").servletPath("/api/v1/auth/login").header("Origin", "https://app.test").header("X-Forwarded-For", "192.0.2.2"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        verifyNoInteractions(sessions);
    }
}
