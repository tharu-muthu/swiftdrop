package com.swiftdrop.service.dispatch;

import com.swiftdrop.dto.booking.BookingSnapshot;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.exception.LifecycleConflictException;
import com.swiftdrop.repository.dispatch.DispatchRepository;
import com.swiftdrop.security.IdentityContext;
import com.swiftdrop.service.booking.BookingReadService;
import com.swiftdrop.service.execution.ParcelLifecycleService;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DispatchServiceTest {
    final DispatchRepository repository=mock(DispatchRepository.class);
    final BookingReadService bookings=mock(BookingReadService.class);
    final ParcelLifecycleService lifecycle=mock(ParcelLifecycleService.class);
    final IdentityAccountService identities=mock(IdentityAccountService.class);
    final DispatchService service=new DispatchService(repository,bookings,lifecycle,identities);
    final UUID booking=UUID.randomUUID(),courier=UUID.randomUUID(),admin=UUID.randomUUID();
    void eligible() {
        when(identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN)).thenReturn(new IdentityContext(admin,UUID.randomUUID(),Set.of("DISPATCHER_ADMIN"),0));
        when(bookings.find(booking)).thenReturn(Optional.of(mock(BookingSnapshot.class))); when(repository.active(courier)).thenReturn(true);
    }
    @Test void callsCurrentIdentityAndLifecycleBeforePersistingAssignment() {
        eligible(); service.assign(booking,courier,null,false);
        var order=inOrder(identities,repository,lifecycle);
        order.verify(identities).requireCurrentRole(IdentityRole.DISPATCHER_ADMIN); order.verify(repository).lockBooking(booking);
        order.verify(identities).requireActiveCourier(courier); order.verify(lifecycle).initialize(booking,courier,admin); order.verify(repository).assign(booking,courier,admin);
    }
    @Test void duplicateAssignmentCannotChangeLifecycle() {
        eligible(); when(repository.assigned(booking)).thenReturn(Optional.of(UUID.randomUUID()));
        assertThatThrownBy(()->service.assign(booking,courier,null,false)).isInstanceOf(LifecycleConflictException.class);
        verifyNoInteractions(lifecycle); verify(repository,never()).assign(any(),any(),any());
    }
    @Test void terminalLifecycleRejectionDoesNotRevokeCurrentAssignment() {
        eligible(); when(repository.assigned(booking)).thenReturn(Optional.of(UUID.randomUUID()));
        when(lifecycle.initialize(booking,courier,admin)).thenThrow(new LifecycleConflictException("Terminal delivery"));
        assertThatThrownBy(()->service.assign(booking,courier,"Vehicle change",true)).isInstanceOf(LifecycleConflictException.class);
        verify(repository,never()).revoke(any(),any()); verify(repository,never()).assign(any(),any(),any());
    }
    @Test void reassignmentLocksLifecycleBeforeChangingAssignmentRows() {
        eligible(); when(repository.assigned(booking)).thenReturn(Optional.of(UUID.randomUUID())); service.assign(booking,courier,"Vehicle change",true);
        var order=inOrder(lifecycle,repository); order.verify(lifecycle).initialize(booking,courier,admin); order.verify(repository).revoke(booking,"Vehicle change"); order.verify(repository).assign(booking,courier,admin);
    }
    @Test void selectableCouriersRequireBothOperationalAndLiveIdentityEligibility() {
        UUID good=UUID.randomUUID(), blocked=UUID.randomUUID(), inactive=UUID.randomUUID();
        when(repository.couriers()).thenReturn(List.of(
                Map.of("courierId",good,"active",true),Map.of("courierId",blocked,"active",true),Map.of("courierId",inactive,"active",false)));
        when(identities.eligibleCourierIds(List.of(good,blocked,inactive))).thenReturn(Set.of(good,inactive));
        when(identities.accountLabels(List.of(good,blocked,inactive))).thenReturn(Map.of(
                good,new com.swiftdrop.dto.identity.AccountLabel("Alex Courier","alex@example.test")));
        var all=service.couriers(false);
        assertThat(all).extracting(row->row.get("eligible")).containsExactly(true,false,false);
        assertThat(all.get(0)).containsEntry("fullName","Alex Courier").containsEntry("email","alex@example.test");
        assertThat(all.get(1)).containsEntry("accountEligible",false);
        assertThat(all.get(2)).containsEntry("accountEligible",true);
        assertThat(all.get(1).get("unavailableReason")).isEqualTo("ACCOUNT_UNAVAILABLE");
        assertThat(all.get(2).get("unavailableReason")).isEqualTo("PROFILE_INACTIVE");
        assertThat(service.couriers(true)).extracting(row->row.get("courierId")).containsExactly(good);
    }
    @Test void newProfilesRequireKnownVehicleCodesButExistingLegacyValuesCanBeRetained() {
        assertThatThrownBy(() -> service.profile(courier,"Motorbike","ABC-123",true))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository,never()).profile(any(),any(),any(),anyBoolean());
        service.profile(courier,"MOTORCYCLE","ABC-123",true);
        verify(repository).profile(courier,"MOTORCYCLE","ABC-123",true);
        when(repository.vehicleType(courier)).thenReturn(Optional.of("Motorbike"));
        service.profile(courier,"Motorbike","ABC-123",false);
        verify(repository).profile(courier,"Motorbike","ABC-123",false);
    }
}
