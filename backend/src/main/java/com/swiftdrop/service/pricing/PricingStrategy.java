package com.swiftdrop.service.pricing;

import com.swiftdrop.dto.pricing.PriceBreakdown;
import com.swiftdrop.entity.pricing.ParcelSize;
import com.swiftdrop.entity.pricing.RateCardVersion;

public interface PricingStrategy {
    PriceBreakdown calculate(RateCardVersion rate, int weightGrams, ParcelSize size);
}