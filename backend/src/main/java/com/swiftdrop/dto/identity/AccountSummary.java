package com.swiftdrop.dto.identity;

import java.util.Set;
import java.util.UUID;

/** Internal service DTO; contains no credentials, email, phone or session secrets. */
public record AccountSummary(UUID id, String fullName, boolean active, Set<String> roles) {
    public AccountSummary { roles = Set.copyOf(roles); }
}
