package com.swiftdrop.dto.identity;

import java.util.Set;
import java.util.UUID;

public record RegistrationResponse(UUID id, String email, String fullName, Set<String> roles) { }
