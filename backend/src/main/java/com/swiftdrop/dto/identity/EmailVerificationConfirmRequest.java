package com.swiftdrop.dto.identity;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record EmailVerificationConfirmRequest(@NotBlank @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String token) {
    @JsonAnySetter public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unexpected field."); }
    @Override public String toString() { return "EmailVerificationConfirmRequest[redacted]"; }
}
