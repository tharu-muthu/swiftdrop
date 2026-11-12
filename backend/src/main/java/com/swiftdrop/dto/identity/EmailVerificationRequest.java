package com.swiftdrop.dto.identity;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerificationRequest(@NotBlank(message = "Enter your email address.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 320, message = "Use at most 320 characters.") String email) {
    public EmailVerificationRequest { email = email == null ? null : email.replaceAll("^ +| +$", ""); }
    @JsonAnySetter public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unexpected field."); }
    @Override public String toString() { return "EmailVerificationRequest[redacted]"; }
}
