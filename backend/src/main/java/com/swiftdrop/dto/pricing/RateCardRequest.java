package com.swiftdrop.dto.pricing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record RateCardRequest(
        @NotNull UUID pickupZoneId,
        @NotNull UUID dropoffZoneId,
        @Min(0) long baseMinor,
        @Min(0) int includedKg,
        @Min(0) long extraKgMinor,
        @Min(0) long sizeSurchargeS,
        @Min(0) long sizeSurchargeM,
        @Min(0) long sizeSurchargeL,
        Instant effectiveFrom) { }