package com.swiftdrop.dto.pricing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ZoneRequest(@NotBlank(message = "Zone name is required.") @Size(max = 150) String name) { }