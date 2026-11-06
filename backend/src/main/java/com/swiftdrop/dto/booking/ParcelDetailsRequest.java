package com.swiftdrop.dto.booking;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Validated, transport-safe M4 input for a later M3 quote call. */
public record ParcelDetailsRequest(
        @NotNull(message = "Weight is required.") @Positive(message = "Weight must be greater than zero.") @Max(value = 100000, message = "Weight must not exceed 100000 grams.") Integer weightGrams,
        @NotBlank(message = "Parcel size is required.") @Pattern(regexp = "^(SMALL|MEDIUM|LARGE|EXTRA_LARGE)$", message = "Choose a valid parcel size.") String size,
        @NotBlank(message = "Parcel description is required.") @Size(max = 200, message = "Parcel description must be at most 200 characters.") String description) { }
