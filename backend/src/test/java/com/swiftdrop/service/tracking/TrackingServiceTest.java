package com.swiftdrop.service.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.swiftdrop.entity.booking.ParcelBooking;
import com.swiftdrop.entity.execution.ParcelLifecycle;
import com.swiftdrop.entity.execution.ParcelStatus;
import com.swiftdrop.entity.execution.ParcelStatusHistory;
import com.swiftdrop.repository.tracking.TrackingBookingRepository;
import com.swiftdrop.repository.tracking.TrackingLifecycleRepository;
import com.swiftdrop.repository.tracking.TrackingStatusHistoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrackingServiceTest {
    private final TrackingBookingRepository bookings = mock(TrackingBookingRepository.class);
    private final TrackingLifecycleRepository lifecycles = mock(TrackingLifecycleRepository.class);
    private final TrackingStatusHistoryRepository history = mock(TrackingStatusHistoryRepository.class);
    private final TrackingService service = new TrackingService(bookings, lifecycles, history);

    @Test
    void returnsSanitizedPublicTimelineForKnownTrackingNumber() {
        var booking = mock(ParcelBooking.class);
        var lifecycle = mock(ParcelLifecycle.class);
        var first = mock(ParcelStatusHistory.class);
        var second = mock(ParcelStatusHistory.class);
        var bookingId = UUID.randomUUID();
        var lifecycleId = UUID.randomUUID();
        var firstTime = Instant.parse("2026-11-07T10:00:00Z");
        var secondTime = Instant.parse("2026-11-07T11:00:00Z");

        when(booking.getId()).thenReturn(bookingId);
        when(booking.getTrackingNumber()).thenReturn(" SD-1001 ");
        when(lifecycle.getId()).thenReturn(lifecycleId);
        when(lifecycle.getStatus()).thenReturn(ParcelStatus.IN_TRANSIT);
        when(lifecycle.getUpdatedAt()).thenReturn(secondTime);
        when(first.getSequenceNo()).thenReturn(1L);
        when(first.getNewStatus()).thenReturn(ParcelStatus.CREATED);
        when(first.getOccurredAt()).thenReturn(firstTime);
        when(second.getSequenceNo()).thenReturn(2L);
        when(second.getNewStatus()).thenReturn(ParcelStatus.IN_TRANSIT);
        when(second.getOccurredAt()).thenReturn(secondTime);
        when(bookings.findByTrackingNumber("SD-1001")).thenReturn(Optional.of(booking));
        when(lifecycles.findByBookingId(bookingId)).thenReturn(Optional.of(lifecycle));
        when(history.findAllByLifecycleIdOrderBySequenceNoAsc(lifecycleId)).thenReturn(List.of(first, second));

        var response = service.publicTracking(" SD-1001 ").orElseThrow();

        assertThat(response.trackingNumber()).isEqualTo(" SD-1001 ");
        assertThat(response.currentStatus()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(response.timeline()).extracting(item -> item.status())
                .containsExactly(ParcelStatus.CREATED, ParcelStatus.IN_TRANSIT);
        verify(history).findAllByLifecycleIdOrderBySequenceNoAsc(lifecycleId);
    }

    @Test
    void returnsEmptyWhenTrackingNumberDoesNotExist() {
        when(bookings.findByTrackingNumber("UNKNOWN")).thenReturn(Optional.empty());

        assertThat(service.publicTracking(" UNKNOWN ")).isEmpty();
        verifyNoInteractions(lifecycles, history);
    }

    @Test
    void returnsEmptyWhenBookingHasNoLifecycleYet() {
        var booking = mock(ParcelBooking.class);
        var bookingId = UUID.randomUUID();
        when(booking.getId()).thenReturn(bookingId);
        when(bookings.findByTrackingNumber("SD-1002")).thenReturn(Optional.of(booking));
        when(lifecycles.findByBookingId(bookingId)).thenReturn(Optional.empty());

        assertThat(service.publicTracking("SD-1002")).isEmpty();
        verifyNoInteractions(history);
    }

    @Test
    void ignoresBlankTrackingNumbersWithoutQueryingRepositories() {
        assertThat(service.publicTracking("  ")).isEmpty();
        verifyNoInteractions(bookings, lifecycles, history);
    }
}
