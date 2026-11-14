package com.swiftdrop.service.pricing;

import com.swiftdrop.dto.pricing.PriceBreakdown;
import com.swiftdrop.entity.pricing.ParcelSize;
import com.swiftdrop.entity.pricing.RateCardVersion;
import com.swiftdrop.exception.PricingErrorCode;
import com.swiftdrop.exception.PricingException;
import org.springframework.stereotype.Component;

/** fee = base + max(0, ceil(grams / 1000) - includedKg) * extraKgRate + sizeSurcharge */
@Component
public class StandardPricingStrategy implements PricingStrategy {

    @Override
    public PriceBreakdown calculate(RateCardVersion rate, int weightGrams, ParcelSize size) {
        if (weightGrams <= 0) {
            throw new PricingException(PricingErrorCode.WEIGHT_NOT_SUPPORTED);
        }
        int billableKg = Math.ceilDiv(weightGrams, 1000);
        long extraKg = Math.max(0, billableKg - rate.getIncludedKg());
        long extraWeight = extraKg * rate.getExtraKgMinor();
        long sizeCharge = rate.surchargeFor(size);
        long total = rate.getBaseMinor() + extraWeight + sizeCharge;
        return new PriceBreakdown(rate.getBaseMinor(), extraWeight, sizeCharge, total, rate.getCurrency());
    }
}