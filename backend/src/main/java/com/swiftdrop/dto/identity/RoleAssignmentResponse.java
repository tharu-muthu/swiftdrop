package com.swiftdrop.dto.identity;

import java.util.Set;
import java.util.UUID;

public record RoleAssignmentResponse(UUID id, Set<String> roles, long version) {
    public RoleAssignmentResponse { roles = Set.copyOf(roles); }
}
