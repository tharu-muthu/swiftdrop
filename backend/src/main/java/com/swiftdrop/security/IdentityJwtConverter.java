package com.swiftdrop.security;

import com.swiftdrop.service.identity.IdentitySessionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class IdentityJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final IdentitySessionService sessions;
    public IdentityJwtConverter(IdentitySessionService sessions) { this.sessions = sessions; }
    @Override public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            Number version = jwt.getClaim("av");
            var identity = sessions.currentIdentity(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("sid")), version.longValue());
            return UsernamePasswordAuthenticationToken.authenticated(identity, null,
                    identity.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList());
        } catch (RuntimeException exception) {
            // Includes database unavailability: never trust stale JWT roles or leak SQL details.
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Session unavailable.", null));
        }
    }
}
