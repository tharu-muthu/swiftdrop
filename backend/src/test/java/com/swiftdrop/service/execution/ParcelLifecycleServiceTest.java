package com.swiftdrop.service.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.dto.execution.StatusUpdateRequest;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.entity.booking.ParcelBooking;
import com.swiftdrop.entity.execution.*;
import com.swiftdrop.repository.booking.ParcelBookingRepository;
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
    private final ParcelBookingRepository bookings = mock(ParcelBookingRepository.class);
    private final ParcelLifecycleService service = new ParcelLifecycleService(identities, lifecycles, history,
            proofs, outbox, bookings, new ObjectMapper());
    private final UUID courier = UUID.randomUUID(), lifecycleId = UUID.randomUUID();

    @ParameterizedTest
    @CsvSource({"CREATED,PICKED_UP", "PICKED_UP,IN_TRANSIT", "IN_TRANSIT,OUT_FOR_DELIVERY",
            "OUT_FOR_DELIVERY,DELIVERED", "OUT_FOR_DELIVERY,FAILED"})
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
        when(lifecycles.findByIdAndAssignedCourierId(lifecycleId, courier)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.details(lifecycleId))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(bookings, history);
    }
}
