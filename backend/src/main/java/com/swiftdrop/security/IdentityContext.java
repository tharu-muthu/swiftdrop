package com.swiftdrop.security;

import java.util.Set;
import java.util.UUID;

/** Trusted, current database identity; never bind this from a request body. */
public record IdentityContext(UUID userId, UUID sessionId, Set<String> roles, long authorizationVersion) {
    public IdentityContext { roles = Set.copyOf(roles); }
}
