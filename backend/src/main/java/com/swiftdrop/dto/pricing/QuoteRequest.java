package com.swiftdrop.dto.pricing;

import com.swiftdrop.entity.pricing.ParcelSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Price-driving inputs only. The amount is always calculated server-side. */
public record QuoteRequest(
        @NotBlank(message = "Pickup postal code is required.") @Size(max = 10) String pickupPostalCode,
        @NotBlank(message = "Drop-off postal code is required.") @Size(max = 10) String dropoffPostalCode,
        @Positive(message = "Weight must be greater than zero.") int weightGrams,
        @NotNull(message = "Parcel size is required.") ParcelSize size) { }