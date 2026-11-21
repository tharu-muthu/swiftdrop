package com.swiftdrop.service.dispatch;

import com.swiftdrop.dto.booking.BookingSnapshot;
import com.swiftdrop.dto.execution.LifecycleSnapshot;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.entity.execution.ParcelStatus;
import com.swiftdrop.service.booking.BookingReadService;
import com.swiftdrop.service.execution.LifecycleReadService;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DispatchQueryServiceTest {
    final BookingReadService bookings = mock(BookingReadService.class);
    final LifecycleReadService lifecycles = mock(LifecycleReadService.class);
    final IdentityAccountService identities = mock(IdentityAccountService.class);
    final Instant now = Instant.parse("2026-11-21T12:00:00Z");
    final Instant cutoff = now.minus(Duration.ofHours(24));
    final DispatchQueryService service = new DispatchQueryService(bookings, lifecycles, identities,
            mock(DispatchService.class), Clock.fixed(now, ZoneOffset.UTC), 24);

    LifecycleSnapshot delivery(ParcelStatus status, Instant updated) {
        UUID bookingId = UUID.randomUUID();
        var booking = mock(BookingSnapshot.class);
        when(booking.id()).thenReturn(bookingId);
        when(bookings.find(bookingId)).thenReturn(Optional.of(booking));
        return new LifecycleSnapshot(UUID.randomUUID(), bookingId, null, status, updated, List.of());
    }

    @Test void overdueRequiresAnOpenDeliveryStrictlyOlderThanTheConfiguredThreshold() {
        var old = delivery(ParcelStatus.IN_TRANSIT, cutoff.minusSeconds(1));
        var boundary = delivery(ParcelStatus.CREATED, cutoff);
        var delivered = delivery(ParcelStatus.DELIVERED, cutoff.minusSeconds(3600));
        var failed = delivery(ParcelStatus.FAILED, cutoff.minusSeconds(3600));
        when(lifecycles.board(0, 50, false, null)).thenReturn(new PageImpl<>(List.of(old, boundary, delivered, failed)));
        var response = service.board(0, 50, false, false);
        @SuppressWarnings("unchecked") var rows = (List<Map<String, Object>>) response.get("items");
        assertThat(rows).extracting(row -> row.get("overdue")).containsExactly(true, false, false, false);
        assertThat(response.get("overdueBefore")).isEqualTo(cutoff);
    }

    @Test void combinesOverdueAndUnassignedFiltersBeforePagination() {
        when(lifecycles.board(2, 10, true, cutoff)).thenReturn(new PageImpl<>(List.of()));
        service.board(2, 10, true, true);
        verify(lifecycles).board(2, 10, true, cutoff);
    }

    @Test void deniesQueriesBeforeReadingAnyParcelData() {
        when(identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN)).thenThrow(new AccessDeniedException("Access denied"));
        assertThatThrownBy(() -> service.board(0, 50, false, true)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(bookings, lifecycles);
    }
}
