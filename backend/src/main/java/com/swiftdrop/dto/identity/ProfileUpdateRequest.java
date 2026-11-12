package com.swiftdrop.dto.identity;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank(message = "Enter your full name.")
        @Size(max = 200, message = "Use at most 200 characters.") String fullName,
        @JsonProperty(required = true)
        @Size(max = 32, message = "Use at most 32 characters.")
        @Pattern(regexp = "^$|^(?=(?:\\D*\\d){7,15}\\D*$)\\+?[0-9][0-9 -]*[0-9]$", message = "Enter a valid phone number or leave it empty.")
        String phone,
        @NotNull(message = "Reload your profile before saving.") @Min(value = 0, message = "Reload your profile before saving.")
        Long version) {
    public ProfileUpdateRequest {
        fullName = fullName == null ? null : fullName.strip();
        phone = phone == null ? null : phone.strip();
    }

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Only fullName, phone and version are accepted.");
    }
}
