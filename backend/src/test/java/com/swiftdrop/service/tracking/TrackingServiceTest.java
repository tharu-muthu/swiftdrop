package com.swiftdrop.service.tracking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.swiftdrop.dto.booking.BookingSnapshot;
import com.swiftdrop.dto.execution.*;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.entity.execution.ParcelStatus;
import com.swiftdrop.security.IdentityContext;
import com.swiftdrop.service.booking.BookingReadService;
import com.swiftdrop.service.execution.LifecycleReadService;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class TrackingServiceTest {
    final BookingReadService bookings=mock(BookingReadService.class);
    final LifecycleReadService lifecycles=mock(LifecycleReadService.class);
    final IdentityAccountService identities=mock(IdentityAccountService.class);
    final TrackingService service=new TrackingService(bookings,lifecycles,identities);
    final UUID id=UUID.randomUUID(),customer=UUID.randomUUID();
    final BookingSnapshot booking=mock(BookingSnapshot.class);
    void fixture() {
        when(bookings.byTrackingNumber("SDP-ABC12345")).thenReturn(Optional.of(booking));
        when(booking.id()).thenReturn(id); when(booking.trackingNumber()).thenReturn("SDP-ABC12345");
        when(booking.customerId()).thenReturn(customer);
        when(lifecycles.find(id)).thenReturn(Optional.of(new LifecycleSnapshot(UUID.randomUUID(),id,null,ParcelStatus.IN_TRANSIT,Instant.EPOCH,
            List.of(new StatusHistoryItem(1,null,ParcelStatus.CREATED,customer,"private reason",Instant.EPOCH)))));
    }
    @Test void publicResponseContainsOnlySafeTimelineFields() {
        fixture(); var response=service.publicTracking(" sdp-abc12345 ").orElseThrow();
        assertThat(response.trackingNumber()).isEqualTo("SDP-ABC12345");
        assertThat(response.timeline()).hasSize(1); assertThat(response.timeline().getFirst().status()).isEqualTo(ParcelStatus.CREATED);
        assertThat(response.toString()).doesNotContain("private reason",customer.toString());
    }
    @Test void rejectsMalformedNumbersWithoutReadingData() {
        for(var number:List.of("", "UNKNOWN", "../tracking", "SDP-X", "SDP-"+"A".repeat(33))) assertThat(service.publicTracking(number)).isEmpty();
        verifyNoInteractions(bookings,lifecycles);
    }
    @Test void missingBookingIsAbsent() { assertThat(service.publicTracking("SDP-UNKNOWN1")).isEmpty(); verifyNoInteractions(lifecycles); }
    @Test void customerSeesOwnPrivateDetails() {
        fixture(); when(identities.requireCurrentRole(IdentityRole.CUSTOMER)).thenReturn(new IdentityContext(customer,UUID.randomUUID(),Set.of("CUSTOMER"),0));
        when(booking.pickupCity()).thenReturn("Colombo"); when(booking.recipientName()).thenReturn("Recipient");
        assertThat(service.customerTracking("SDP-ABC12345").orElseThrow().recipientName()).isEqualTo("Recipient");
    }
    @Test void customerCannotSeeAnotherOwnerOrMerchantBooking() {
        fixture(); when(identities.requireCurrentRole(IdentityRole.CUSTOMER)).thenReturn(new IdentityContext(UUID.randomUUID(),UUID.randomUUID(),Set.of("CUSTOMER"),0));
        assertThat(service.customerTracking("SDP-ABC12345")).isEmpty();
        when(booking.customerId()).thenReturn(null); assertThat(service.customerTracking("SDP-ABC12345")).isEmpty();
        verifyNoInteractions(lifecycles);
    }
}
