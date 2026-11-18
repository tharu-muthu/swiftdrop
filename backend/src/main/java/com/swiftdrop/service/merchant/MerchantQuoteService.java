package com.swiftdrop.service.merchant;

import com.swiftdrop.dto.pricing.QuoteRequest;
import com.swiftdrop.dto.pricing.QuoteResponse;
import com.swiftdrop.service.pricing.PricingService;
import com.swiftdrop.service.pricing.QuoteOwner;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** MER-04: merchant quotes through the shared M3 pricing service. The merchant always comes from the API key. */
@Service
public class MerchantQuoteService {
    static final String QUOTE_SCOPE = "quote";

    private final MerchantAccessService access;
    private final PricingService pricing;

    public MerchantQuoteService(MerchantAccessService access, PricingService pricing) {
        this.access = access;
        this.pricing = pricing;
    }

    public QuoteResponse create(QuoteRequest request) {
        return pricing.createQuote(currentOwner(), request);
    }

    public QuoteResponse get(UUID quoteId) {
        return pricing.getQuote(quoteId, currentOwner());
    }

    private QuoteOwner currentOwner() {
        var merchant = access.requireCurrentApiMerchant();
        if (!merchant.scopes().contains(QUOTE_SCOPE)) {
            throw new AccessDeniedException("Access denied");
        }
        return QuoteOwner.merchant(merchant.merchantId());
    }
}