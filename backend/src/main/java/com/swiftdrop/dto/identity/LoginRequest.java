package com.swiftdrop.dto.identity;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotNull @Size(max = 320) String email,
        @NotNull @Size(max = 256) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {
    // Login does not enforce today's signup minimum against an existing credential.
    @JsonAnySetter public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unexpected login field.");
    }
    @Override public String toString() { return "LoginRequest[redacted]"; }
}
