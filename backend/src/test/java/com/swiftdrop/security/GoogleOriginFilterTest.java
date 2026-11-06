package com.swiftdrop.security;

import com.swiftdrop.config.GoogleSettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class GoogleOriginFilterTest {
    private final GoogleOriginFilter filter = new GoogleOriginFilter(
            new GoogleSettings(true, "fixture", "fixture", "http://localhost:8080"));
    @Test void alternateAndHostileHostsRedirectBeforeStateIsCreated() throws Exception {
        for (String host : java.util.List.of("127.0.0.1", "attacker.example")) {
            var request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
            request.setServletPath("/oauth2/authorization/google"); request.setServerName(host); request.setServerPort(8080);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (a, b) -> { throw new AssertionError("Must canonicalize first"); });
            assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:8080/oauth2/authorization/google");
            assertThat(request.getSession(false)).isNull();
        }
    }
    @Test void canonicalOriginContinuesAndWrongCallbackOriginFails() throws Exception {
        var request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.setServletPath("/oauth2/authorization/google"); request.setServerName("localhost"); request.setServerPort(8080);
        boolean[] called = {false};
        filter.doFilter(request, new MockHttpServletResponse(), (a, b) -> called[0] = true);
        assertThat(called[0]).isTrue();
        request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        request.setServletPath("/login/oauth2/code/google"); request.setServerName("attacker.example");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (a, b) -> { throw new AssertionError("Must reject"); });
        assertThat(response.getStatus()).isEqualTo(400);
    }
}
