package com.swiftdrop.dto.identity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AdminUserResponse(UUID id, String fullName, String email, String phone,
        Set<String> roles, boolean active, boolean emailVerified, Instant createdAt,
        Instant updatedAt, long version) { }
