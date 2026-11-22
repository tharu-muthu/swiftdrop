package com.swiftdrop.service.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.dto.execution.StatusUpdateRequest;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.dto.booking.BookingSnapshot;
import com.swiftdrop.entity.execution.*;
import com.swiftdrop.exception.LifecycleConflictException;
import com.swiftdrop.service.booking.BookingReadService;
import com.swiftdrop.service.dispatch.DispatchCompletionService;
import com.swiftdrop.repository.execution.*;
import com.swiftdrop.security.IdentityContext;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParcelLifecycleServiceTest {
    private final IdentityAccountService identities = mock(IdentityAccountService.class);
    private final ParcelLifecycleRepository lifecycles = mock(ParcelLifecycleRepository.class);
    private final ParcelStatusHistoryRepository history = mock(ParcelStatusHistoryRepository.class);
    private final DeliveryProofRepository proofs = mock(DeliveryProofRepository.class);
    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    private final BookingReadService bookings = mock(BookingReadService.class);
    private final com.swiftdrop.service.dispatch.CourierAssignmentQuery assignments = mock(com.swiftdrop.service.dispatch.CourierAssignmentQuery.class);
    private final ParcelLifecycleService service = new ParcelLifecycleService(identities, lifecycles, history,
            proofs, outbox, bookings, new ObjectMapper().findAndRegisterModules(), mock(DispatchCompletionService.class), assignments,
            java.time.Clock.fixed(java.time.Instant.parse("2026-11-22T10:00:00Z"), java.time.ZoneOffset.UTC), "Asia/Colombo");
    private final UUID courier = UUID.randomUUID(), lifecycleId = UUID.randomUUID();

    @ParameterizedTest
    @CsvSource({"CREATED,PICKED_UP", "PICKED_UP,IN_TRANSIT", "IN_TRANSIT,OUT_FOR_DELIVERY",
            "OUT_FOR_DELIVERY,DELIVERED", "OUT_FOR_DELIVERY,FAILED", "PICKED_UP,FAILED", "IN_TRANSIT,FAILED"})
    void acceptsEverySupportedTransition(ParcelStatus expected, ParcelStatus next) {
        var request = new StatusUpdateRequest(expected, next,
                next == ParcelStatus.DELIVERED ? "Recipient" : null,
                next == ParcelStatus.FAILED ? "Nobody home" : null);
        assertThatCode(() -> ParcelStatusTransitionValidator.validate(expected, next, request))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSkippedTransitionWithoutWritingAnything() {
        var lifecycle = mock(ParcelLifecycle.class);
        when(lifecycles.findByIdAndAssignedCourierId(lifecycleId, courier)).thenReturn(Optional.of(lifecycle));
        when(lifecycle.getStatus()).thenReturn(ParcelStatus.CREATED);
        when(identities.requireCurrentRole(IdentityRole.COURIER))
                .thenReturn(new IdentityContext(courier, UUID.randomUUID(), Set.of("COURIER"), 0));

        assertThatThrownBy(() -> service.update(lifecycleId,
                new StatusUpdateRequest(ParcelStatus.CREATED, ParcelStatus.IN_TRANSIT, null, null)))
                .isInstanceOf(com.swiftdrop.exception.LifecycleConflictException.class);
        verify(lifecycles, never()).saveAndFlush(any());
        verifyNoInteractions(history, proofs, outbox);
    }

    @Test
    void requiresProofBeforeSuccessfulDelivery() {
        var lifecycle = mock(ParcelLifecycle.class);
        when(lifecycles.findByIdAndAssignedCourierId(lifecycleId, courier)).thenReturn(Optional.of(lifecycle));
        when(lifecycle.getStatus()).thenReturn(ParcelStatus.OUT_FOR_DELIVERY);
        when(identities.requireCurrentRole(IdentityRole.COURIER))
                .thenReturn(new IdentityContext(courier, UUID.randomUUID(), Set.of("COURIER"), 0));

        assertThatThrownBy(() -> service.update(lifecycleId,
                new StatusUpdateRequest(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED, " ", null)))
                .isInstanceOf(com.swiftdrop.exception.LifecycleConflictException.class)
                .hasMessage("Recipient name is required for delivery confirmation.");
        verify(lifecycles, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAccessToAnotherCouriersDelivery() {
        when(identities.requireCurrentRole(IdentityRole.COURIER))
                .thenReturn(new IdentityContext(courier, UUID.randomUUID(), Set.of("COURIER"), 0));
        when(lifecycles.findFirstByIdAndAssignedCourierId(lifecycleId, courier)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.details(lifecycleId))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(bookings, history);
    }

    @Test
    void createsLifecycleAndInitialHistoryDuringCourierAssignment() {
        UUID bookingId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(lifecycles.lockByBookingId(bookingId)).thenReturn(Optional.empty());
        var lifecycle = mock(ParcelLifecycle.class);
        when(lifecycle.getId()).thenReturn(lifecycleId);
        when(lifecycle.getBookingId()).thenReturn(bookingId);
        var booking=mock(BookingSnapshot.class);
        when(booking.trackingNumber()).thenReturn("SDP-ABC12345");
        when(bookings.find(bookingId)).thenReturn(Optional.of(booking));
        when(lifecycles.save(any(ParcelLifecycle.class))).thenReturn(lifecycle);
        when(history.save(any(com.swiftdrop.entity.execution.ParcelStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID result = service.initialize(bookingId, courier, actorId);

        assertThat(result).isEqualTo(lifecycleId);
        verify(lifecycles).save(any(ParcelLifecycle.class));
        verify(history).save(any(com.swiftdrop.entity.execution.ParcelStatusHistory.class));
    }

    @Test
    void reassignsExistingLifecycleBeforePickup() {
        UUID bookingId = UUID.randomUUID();
        var lifecycle = mock(ParcelLifecycle.class);
        when(lifecycle.getId()).thenReturn(lifecycleId);
        when(lifecycle.getStatus()).thenReturn(ParcelStatus.CREATED);
        when(lifecycles.lockByBookingId(bookingId)).thenReturn(Optional.of(lifecycle));

        UUID result = service.initialize(bookingId, courier, UUID.randomUUID());

        assertThat(result).isEqualTo(lifecycleId);
        verify(lifecycle).assignCourier(courier);
        verify(lifecycles).save(lifecycle);
        verifyNoInteractions(history);
    }

    @Test
    void rejectsReassignmentOfTerminalLifecycle() {
        UUID bookingId = UUID.randomUUID();
        var lifecycle = mock(ParcelLifecycle.class);
        when(lifecycle.getStatus()).thenReturn(ParcelStatus.DELIVERED);
        when(lifecycles.lockByBookingId(bookingId)).thenReturn(Optional.of(lifecycle));

        assertThatThrownBy(() -> service.initialize(bookingId, courier, UUID.randomUUID()))
                .isInstanceOf(LifecycleConflictException.class)
                .hasMessage("Terminal deliveries cannot be reassigned.");
        verify(lifecycles, never()).save(any());
    }
    @Test void rejectsReassignmentAfterPickup() {
        UUID bookingId = UUID.randomUUID();
        var lifecycle = mock(ParcelLifecycle.class);
        when(lifecycle.getStatus()).thenReturn(ParcelStatus.PICKED_UP);
        when(lifecycle.getAssignedCourierId()).thenReturn(UUID.randomUUID());
        when(lifecycles.lockByBookingId(bookingId)).thenReturn(Optional.of(lifecycle));
        assertThatThrownBy(() -> service.initialize(bookingId, courier, UUID.randomUUID()))
                .isInstanceOf(LifecycleConflictException.class).hasMessageContaining("before pickup");
        verify(lifecycles, never()).save(any());
    }
    @Test void workdayUsesColomboMidnightAndKeepsCurrentCarryover() {
        when(identities.requireCurrentRole(IdentityRole.COURIER))
                .thenReturn(new IdentityContext(courier, UUID.randomUUID(), Set.of("COURIER"), 0));
        var start = java.time.Instant.parse("2026-11-21T18:30:00Z");
        var end = java.time.Instant.parse("2026-11-22T18:30:00Z");
        when(assignments.bookingsAssignedBetween(start, end)).thenReturn(List.of());
        service.assignedDeliveries(null);
        verify(lifecycles).findCourierWorkday(courier, List.of(), start, end, true);
    }
    @Test void rejectsFutureWorkdaysWithoutReadingDeliveries() {
        when(identities.requireCurrentRole(IdentityRole.COURIER))
                .thenReturn(new IdentityContext(courier, UUID.randomUUID(), Set.of("COURIER"), 0));
        assertThatThrownBy(() -> service.assignedDeliveries(java.time.LocalDate.parse("2026-11-23")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(assignments, lifecycles, bookings);
    }
}
