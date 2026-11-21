package com.swiftdrop.dto.pricing;

import java.time.Instant;
import java.util.UUID;

public record RateCardResponse(UUID rateVersionId, UUID pickupZoneId, UUID dropoffZoneId, String currency,
        long baseMinor, int includedKg, long extraKgMinor, long sizeSurchargeSmall, long sizeSurchargeMedium,
        long sizeSurchargeLarge, long sizeSurchargeExtraLarge, Instant effectiveFrom, boolean active) { }