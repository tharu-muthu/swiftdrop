package com.swiftdrop.service.pricing;

import java.util.UUID;

/** Owner of a quote: a customer user ID or an approved merchant ID. Always derived server-side. */
public record QuoteOwner(UUID id, boolean merchant) {
    public static QuoteOwner customer(UUID userId) { return new QuoteOwner(userId, false); }
    public static QuoteOwner merchant(UUID merchantId) { return new QuoteOwner(merchantId, true); }
}