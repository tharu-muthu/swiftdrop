package com.swiftdrop.service.pricing;

import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.dto.pricing.QuoteRequest;
import com.swiftdrop.dto.pricing.QuoteResponse;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Browser pricing entry point; partner callers use the merchant access contract. */
@Service
public class CustomerQuoteService {
    private final PricingService pricing;
    private final IdentityAccountService identities;

    public CustomerQuoteService(PricingService pricing, IdentityAccountService identities) {
        this.pricing = pricing;
        this.identities = identities;
    }

    @Transactional
    public QuoteResponse create(QuoteRequest request) {
        return pricing.createQuote(owner(), request);
    }

    @Transactional(readOnly = true)
    public QuoteResponse get(UUID quoteId) {
        return pricing.getQuote(quoteId, owner());
    }

    private QuoteOwner owner() {
        return QuoteOwner.customer(identities.requireCurrentRole(IdentityRole.CUSTOMER).userId());
    }
}
