package com.swiftdrop.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AuthSettings.class, GoogleSettings.class})
public class JwtConfig {
    @Bean public Clock identityClock() { return Clock.systemUTC(); }

    private SecretKeySpec key(AuthSettings settings) {
        try {
            byte[] bytes = Base64.getDecoder().decode(settings.jwtSecret());
            if (bytes.length < 32) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes, "HmacSHA256");
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Set AUTH_JWT_SECRET to a private Base64-encoded random key of at least 32 bytes.");
        }
    }
    @Bean public JwtEncoder jwtEncoder(AuthSettings settings) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key(settings)));
    }
    @Bean public JwtDecoder jwtDecoder(AuthSettings settings, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(key(settings)).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            try {
                UUID.fromString(jwt.getSubject());
                UUID.fromString(jwt.getClaimAsString("sid"));
                UUID.fromString(jwt.getId());
                Number version = jwt.getClaim("av");
                if (!(version instanceof Long || version instanceof Integer) || version.longValue() < 0 || !jwt.getAudience().contains(settings.audience())
                        || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null
                        || jwt.getIssuedAt().isAfter(clock.instant())
                        || !jwt.getExpiresAt().isAfter(clock.instant())
                        || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                        || jwt.getExpiresAt().isAfter(jwt.getIssuedAt().plus(settings.accessTtl()))) throw new IllegalArgumentException();
                return OAuth2TokenValidatorResult.success();
            } catch (RuntimeException exception) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid access token.", null));
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(settings.issuer()), claims));
        return decoder;
    }
}
