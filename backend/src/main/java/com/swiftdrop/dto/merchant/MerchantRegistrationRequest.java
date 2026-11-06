package com.swiftdrop.dto.merchant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MerchantRegistrationRequest(
        @NotBlank @Size(max = 150) String businessName,
        @NotBlank @Email @Size(max = 254) String contactEmail,
        @Size(max = 30) @Pattern(regexp = "^[0-9+()\\-\\s]*$", message = "Invalid phone number") String contactPhone,
        @Size(max = 300) String businessAddress) { }