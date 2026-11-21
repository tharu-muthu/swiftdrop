package com.swiftdrop.service.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.dto.pricing.ZoneRequest;
import com.swiftdrop.entity.pricing.Zone;
import com.swiftdrop.exception.PricingErrorCode;
import com.swiftdrop.exception.PricingException;
import com.swiftdrop.repository.pricing.RateCardRepository;
import com.swiftdrop.repository.pricing.ZonePostcodeRepository;
import com.swiftdrop.repository.pricing.ZoneRepository;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class PricingAdminServiceTest {
    private final ZoneRepository zones = mock(ZoneRepository.class);
    private final ZonePostcodeRepository postcodes = mock(ZonePostcodeRepository.class);
    private final RateCardRepository rateCards = mock(RateCardRepository.class);
    private final IdentityAccountService identities = mock(IdentityAccountService.class);
    private final PricingAdminService service = new PricingAdminService(zones, postcodes, rateCards, identities,
            Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void nonAdminCannotCreateZone() {
        when(identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN)).thenThrow(new AccessDeniedException("no"));

        assertThrows(AccessDeniedException.class, () -> service.createZone(new ZoneRequest("Kandy")));
        verify(zones, never()).save(any());
    }

    @Test
    void adminCanCreateZone() {
        when(zones.existsByName("Kandy")).thenReturn(false);
        when(zones.save(any(Zone.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals("Kandy", service.createZone(new ZoneRequest("  Kandy ")).name());
    }

    @Test
    void duplicateZoneNameIsRejected() {
        when(zones.existsByName("Kandy")).thenReturn(true);

        var error = assertThrows(PricingException.class, () -> service.createZone(new ZoneRequest("Kandy")));
        assertEquals(PricingErrorCode.ZONE_NAME_TAKEN, error.code());
    }
}