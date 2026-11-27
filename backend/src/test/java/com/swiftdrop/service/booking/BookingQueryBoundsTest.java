package com.swiftdrop.service.booking;

import com.swiftdrop.dto.execution.CustomerDeliveryCounts;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.entity.booking.ParcelBooking;
import com.swiftdrop.repository.booking.ParcelBookingRepository;
import com.swiftdrop.security.IdentityContext;
import com.swiftdrop.service.execution.LifecycleReadService;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BookingQueryBoundsTest {
    @Test void historyFetchesLifecycleDetailsOnlyForTheRequestedPage() {
        var identities = mock(IdentityAccountService.class);
        var bookings = mock(ParcelBookingRepository.class);
        var lifecycles = mock(LifecycleReadService.class);
        UUID owner = UUID.randomUUID(), bookingId = UUID.randomUUID();
        when(identities.requireCurrentRole(IdentityRole.CUSTOMER))
                .thenReturn(new IdentityContext(owner, UUID.randomUUID(), Set.of("CUSTOMER"), 0));
        var booking = mock(ParcelBooking.class);
        when(booking.getId()).thenReturn(bookingId);
        when(bookings.findHistory(eq(owner), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(booking), PageRequest.of(0, 1), 10000));
        when(lifecycles.customerCounts(owner)).thenReturn(new CustomerDeliveryCounts(10000, 300, 200, 9700));
        when(lifecycles.summaries(List.of(bookingId))).thenReturn(Map.of());
        var result = new BookingHistoryService(identities, bookings, lifecycles)
                .history(null, null, null, null, 0, 1);
        assertThat(result.bookings()).hasSize(1);
        verify(bookings).findHistory(eq(owner), isNull(), isNull(), isNull(), isNull(),
                argThat(page -> page.isPaged() && page.getPageSize() == 1));
        verify(lifecycles).customerCounts(owner);
        verify(lifecycles).summaries(List.of(bookingId));
        verifyNoMoreInteractions(bookings, lifecycles);
    }
}
