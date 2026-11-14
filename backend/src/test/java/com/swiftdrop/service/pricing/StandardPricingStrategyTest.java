package com.swiftdrop.service.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swiftdrop.dto.pricing.PriceBreakdown;
import com.swiftdrop.entity.pricing.ParcelSize;
import com.swiftdrop.entity.pricing.RateCardVersion;
import com.swiftdrop.exception.PricingException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StandardPricingStrategyTest {
    // base 45000, 1 kg included, 15000 per extra kg, surcharges S=0 M=10000 L=25000
    private final RateCardVersion rate = new RateCardVersion(UUID.randomUUID(), UUID.randomUUID(),
            45_000, 1, 15_000, 0, 10_000, 25_000, 40_000, Instant.parse("2026-01-01T00:00:00Z"));
    private final StandardPricingStrategy strategy = new StandardPricingStrategy();

    @Test
    void weightAt999And1000GramsBillsOneKg() {
        assertEquals(45_000, strategy.calculate(rate, 999, ParcelSize.SMALL).totalMinor());
        assertEquals(45_000, strategy.calculate(rate, 1_000, ParcelSize.SMALL).totalMinor());
    }

    @Test
    void weightAt1001GramsBillsTwoKg() {
        PriceBreakdown price = strategy.calculate(rate, 1_001, ParcelSize.SMALL);
        assertEquals(15_000, price.extraWeightMinor());
        assertEquals(60_000, price.totalMinor());
    }

    @Test
    void sizeSurchargeIsAdded() {
        PriceBreakdown price = strategy.calculate(rate, 2_500, ParcelSize.LARGE); // 3 kg billed, 2 extra
        assertEquals(45_000 + 30_000 + 25_000, price.totalMinor());
        assertEquals("LKR", price.currency());
    }

    @Test
    void zeroOrNegativeWeightRejected() {
        assertThrows(PricingException.class, () -> strategy.calculate(rate, 0, ParcelSize.SMALL));
        assertThrows(PricingException.class, () -> strategy.calculate(rate, -5, ParcelSize.SMALL));
    }
}