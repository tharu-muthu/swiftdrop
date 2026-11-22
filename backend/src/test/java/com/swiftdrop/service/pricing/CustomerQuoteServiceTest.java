package com.swiftdrop.service.pricing;

import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.service.identity.IdentityAccountService;
import com.swiftdrop.security.IdentityContext;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerQuoteServiceTest {
    final PricingService pricing = mock(PricingService.class);
    final IdentityAccountService identities = mock(IdentityAccountService.class);
    final CustomerQuoteService service = new CustomerQuoteService(pricing, identities);
    @Test void nonCustomersCannotCreateOrReadCustomerQuotes() {
        when(identities.requireCurrentRole(IdentityRole.CUSTOMER)).thenThrow(new AccessDeniedException("Access denied"));
        assertThatThrownBy(() -> service.create(null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(pricing);
    }
    @Test void quoteOwnerAlwaysComesFromTheCurrentCustomer() {
        UUID owner = UUID.randomUUID(), quote = UUID.randomUUID();
        when(identities.requireCurrentRole(IdentityRole.CUSTOMER)).thenReturn(new IdentityContext(owner, UUID.randomUUID(), Set.of("CUSTOMER"), 0));
        service.get(quote);
        verify(pricing).getQuote(quote, QuoteOwner.customer(owner));
    }
}
