package com.swiftdrop.dto.pricing;

import java.time.Instant;
import java.util.UUID;

public record QuoteResponse(UUID quoteId, long amountMinor, String currency, long baseMinor,
        long extraWeightMinor, long sizeSurchargeMinor, Instant expiresAt) { }