package com.swiftdrop.service.identity;

import com.swiftdrop.config.AuthSettings;
import com.swiftdrop.dto.identity.TokenResponse;
import com.swiftdrop.entity.identity.AuthSession;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AccessTokenService {
    private final JwtEncoder encoder;
    private final AuthSettings settings;
    private final Clock clock;
    public AccessTokenService(JwtEncoder encoder, AuthSettings settings, Clock clock) {
        this.encoder = encoder; this.settings = settings; this.clock = clock;
    }
    public TokenResponse issue(AuthSession session) {
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var expiry = now.plus(settings.accessTtl());
        if (expiry.isAfter(session.getExpiresAt())) expiry = session.getExpiresAt().truncatedTo(ChronoUnit.SECONDS);
        var claims = JwtClaimsSet.builder().issuer(settings.issuer()).audience(List.of(settings.audience()))
                .subject(session.getUserId().toString()).id(UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(expiry).claim("sid", session.getId().toString())
                .claim("av", session.getAuthorizationVersion()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", Math.max(0, ChronoUnit.SECONDS.between(now, expiry)));
    }
}
