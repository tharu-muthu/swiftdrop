package com.swiftdrop.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class JwtConfigTest {
    private final String fixtureKey = Base64.getEncoder().encodeToString(new byte[64]);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
    private final AuthSettings settings = new AuthSettings("https://issuer.test", "api", fixtureKey,
            Duration.ofMinutes(10), Duration.ofDays(7), false, List.of("https://app.test"));
    @ParameterizedTest @ValueSource(strings = {"HS384", "HS512"})
    void rejectsCorrectlySignedButUnapprovedAlgorithms(String algorithm) {
        var config = new JwtConfig();
        var claims = JwtClaimsSet.builder().issuer(settings.issuer()).audience(List.of("api"))
                .subject(UUID.randomUUID().toString()).id(UUID.randomUUID().toString())
                .issuedAt(clock.instant()).expiresAt(clock.instant().plusSeconds(600))
                .claim("sid", UUID.randomUUID().toString()).claim("av", 0L).build();
        String signed = config.jwtEncoder(settings).encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.from(algorithm)).build(), claims)).getTokenValue();
        assertThatThrownBy(() -> config.jwtDecoder(settings, clock).decode(signed)).isInstanceOf(JwtException.class);
    }
    @Test void noWeakOrMissingSigningKeyFallbackExists() {
        var bad = new AuthSettings("https://issuer.test", "api", "", Duration.ofMinutes(10), Duration.ofDays(7), false, List.of("https://app.test"));
        assertThatThrownBy(() -> new JwtConfig().jwtEncoder(bad)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AUTH_JWT_SECRET");
    }
    @Test void insecureDevelopmentCannotBeEnabledForNonLoopbackOrigins() {
        assertThatThrownBy(() -> new AuthSettings("issuer", "api", fixtureKey, Duration.ofMinutes(10), Duration.ofDays(7),
                true, List.of("http://example.com"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthSettings("issuer", "api", fixtureKey, Duration.ofMinutes(10), Duration.ofDays(7),
                false, List.of("http://localhost:5173"))).isInstanceOf(IllegalArgumentException.class);
    }
}
