package com.swiftdrop.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import static org.assertj.core.api.Assertions.*;

class GoogleCallbackOriginTest {
    @Test void callbackNeverUsesTheRequestHostOrForwardedHeaders() {
        var settings = new GoogleSettings(true, "fixture", "fixture", "http://localhost:8080");
        var registrations = new GoogleClientConfig().googleClientRegistration(settings);
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        var request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.setServletPath("/oauth2/authorization/google"); request.setServerName("attacker.example");
        request.addHeader("Host", "attacker.example"); request.addHeader("X-Forwarded-Host", "attacker.example");
        assertThat(resolver.resolve(request).getRedirectUri()).isEqualTo("http://localhost:8080/login/oauth2/code/google");
    }
    @Test void malformedOriginsAreRejectedAndSecretsAreRedacted() {
        for (String origin : java.util.List.of("http://attacker.example", "https://example.test/path", "https://user@example.test")) {
            assertThatThrownBy(() -> new GoogleSettings(true, "fixture", "secret-fixture", origin))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new GoogleSettings(true, "fixture", "secret-fixture", "https://example.test").toString())
                .doesNotContain("secret-fixture");
    }
}
