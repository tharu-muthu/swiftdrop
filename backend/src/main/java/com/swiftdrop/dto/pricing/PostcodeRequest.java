package com.swiftdrop.dto.pricing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostcodeRequest(@NotBlank(message = "Postal code is required.") @Size(max = 10) String postcode) { }