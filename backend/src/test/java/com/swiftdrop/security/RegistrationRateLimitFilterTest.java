package com.swiftdrop.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class RegistrationRateLimitFilterTest {
    @Test
    void limitsAttemptsWithSafe429AndDoesNotTrustForwardedAddresses() throws Exception {
        var filter = new RegistrationRateLimitFilter(new ObjectMapper(), 2);
        var calls = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
            request.setServletPath("/api/v1/auth/register");
            request.addHeader("X-Forwarded-For", "192.0.2." + i);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> calls.incrementAndGet());
            if (i == 2) {
                assertThat(response.getStatus()).isEqualTo(429);
                assertThat(response.getHeader("Retry-After")).isEqualTo("600");
                assertThat(response.getContentAsString()).contains("RATE_LIMITED", "correlationId");
            }
        }
        assertThat(calls).hasValue(2);
    }
    @Test
    void doesNotThrottleThePublicHealthEndpoint() throws Exception {
        var filter = new RegistrationRateLimitFilter(new ObjectMapper(), 0);
        var request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.setServletPath("/api/v1/health");
        var calls = new AtomicInteger();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> calls.incrementAndGet());
        assertThat(calls).hasValue(1);
    }
}
