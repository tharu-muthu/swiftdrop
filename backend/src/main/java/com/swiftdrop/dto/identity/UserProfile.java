package com.swiftdrop.dto.identity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserProfile(UUID id, String email, String fullName, String phone, Set<String> roles,
                          boolean active, Instant createdAt, Instant updatedAt, long version,
                          boolean passwordConfigured, boolean googleConnected) { }
