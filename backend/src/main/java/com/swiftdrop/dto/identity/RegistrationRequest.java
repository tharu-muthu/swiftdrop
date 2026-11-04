package com.swiftdrop.dto.identity;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.validation.identity.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank(message = "Enter your full name.") @Size(max = 200, message = "Use at most 200 characters.")
        String fullName,
        @NotBlank(message = "Enter your email address.") @Email(message = "Enter a valid email address.")
        @Size(max = 320, message = "Use at most 320 characters.") String email,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @ValidPassword String password) {
    public RegistrationRequest {
        fullName = fullName == null ? null : fullName.strip();
        // Match PostgreSQL btrim's ordinary-space behavior. Password is never normalized.
        email = email == null ? null : email.replaceAll("^ +| +$", "");
    }
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Only fullName, email and password are accepted.");
    }
    @Override
    public String toString() { return "RegistrationRequest[redacted]"; }
}
