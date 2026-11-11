package com.swiftdrop.dto.pricing;

public record PriceBreakdown(long baseMinor, long extraWeightMinor, long sizeSurchargeMinor,
        long totalMinor, String currency) { }