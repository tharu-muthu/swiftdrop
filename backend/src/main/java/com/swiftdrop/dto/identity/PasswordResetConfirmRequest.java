package com.swiftdrop.dto.identity;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.validation.identity.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirmRequest(
        @NotBlank @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String token,
        @ValidPassword @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {
    @JsonAnySetter public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unexpected field."); }
    @Override public String toString() { return "PasswordResetConfirmRequest[redacted]"; }
}
