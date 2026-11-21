package com.swiftdrop.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.dto.merchant.MerchantContext;
import com.swiftdrop.dto.pricing.QuoteRequest;
import com.swiftdrop.dto.pricing.QuoteResponse;
import com.swiftdrop.entity.pricing.ParcelSize;
import com.swiftdrop.exception.InvalidApiKeyException;
import com.swiftdrop.exception.MerchantNotApprovedException;
import com.swiftdrop.service.pricing.PricingService;
import com.swiftdrop.service.pricing.QuoteOwner;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class MerchantQuoteServiceTest {
    final MerchantAccessService access = mock(MerchantAccessService.class);
    final PricingService pricing = mock(PricingService.class);
    final MerchantQuoteService service = new MerchantQuoteService(access, pricing);

    final UUID merchantId = UUID.randomUUID();
    final QuoteRequest request = new QuoteRequest("00100", "10100", 1500, ParcelSize.SMALL);
    final QuoteResponse quote =
            new QuoteResponse(UUID.randomUUID(), 45000, "LKR", 40000, 5000, 0, Instant.parse("2026-11-18T10:10:00Z"));

    MerchantContext merchant(Set<String> scopes) {
        return new MerchantContext(merchantId, UUID.randomUUID(), "Shop", UUID.randomUUID(), scopes);
    }

    @Test
    void quoteIsPricedForTheMerchantFromTheKeyNeverFromTheRequest() {
        when(access.requireCurrentApiMerchant()).thenReturn(merchant(Set.of("quote", "order")));
        when(pricing.createQuote(QuoteOwner.merchant(merchantId), request)).thenReturn(quote);

        var result = service.create(request);

        assertThat(result).isEqualTo(quote);
        verify(pricing).createQuote(QuoteOwner.merchant(merchantId), request);
    }

    @Test
    void keyWithoutTheQuoteScopeIsDeniedBeforePricingIsTouched() {
        when(access.requireCurrentApiMerchant()).thenReturn(merchant(Set.of("order")));

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(pricing);
    }

    @Test
    void revokedKeyStopsAtTheAccessCheck() {
        when(access.requireCurrentApiMerchant()).thenThrow(new InvalidApiKeyException());

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(InvalidApiKeyException.class);
        verifyNoInteractions(pricing);
    }

    @Test
    void businessThatIsNoLongerApprovedCannotGetQuotes() {
        when(access.requireCurrentApiMerchant()).thenThrow(new MerchantNotApprovedException());

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(MerchantNotApprovedException.class);
        verifyNoInteractions(pricing);
    }

    @Test
    void readingAQuoteIsScopedToTheSameMerchantOwner() {
        var quoteId = UUID.randomUUID();
        when(access.requireCurrentApiMerchant()).thenReturn(merchant(Set.of("quote")));
        when(pricing.getQuote(quoteId, QuoteOwner.merchant(merchantId))).thenReturn(quote);

        assertThat(service.get(quoteId)).isEqualTo(quote);
        verify(pricing).getQuote(quoteId, QuoteOwner.merchant(merchantId));
    }

    @Test
    void readingRequiresTheQuoteScopeToo() {
        when(access.requireCurrentApiMerchant()).thenReturn(merchant(Set.of("order")));

        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(pricing);
    }
}