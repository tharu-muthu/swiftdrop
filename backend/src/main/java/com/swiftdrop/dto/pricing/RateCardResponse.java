package com.swiftdrop.dto.pricing;

import java.time.Instant;
import java.util.UUID;

public record RateCardResponse(UUID rateVersionId, UUID pickupZoneId, UUID dropoffZoneId, String currency,
        long baseMinor, int includedKg, long extraKgMinor, long sizeSurchargeS, long sizeSurchargeM,
        long sizeSurchargeL, Instant effectiveFrom, boolean active) { }