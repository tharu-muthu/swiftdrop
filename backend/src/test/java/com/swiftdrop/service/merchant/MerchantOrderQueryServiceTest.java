package com.swiftdrop.service.merchant;

import com.swiftdrop.dto.booking.BookingSnapshot;
import com.swiftdrop.dto.execution.LifecycleSnapshot;
import com.swiftdrop.dto.merchant.MerchantContext;
import com.swiftdrop.entity.execution.ParcelStatus;
import com.swiftdrop.service.booking.BookingService;
import com.swiftdrop.service.execution.LifecycleReadService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerchantOrderQueryServiceTest {
    final BookingService bookings = mock(BookingService.class);
    final LifecycleReadService lifecycles = mock(LifecycleReadService.class);
    final MerchantOrderQueryService service = new MerchantOrderQueryService(bookings, lifecycles);
    final MerchantContext merchant = new MerchantContext(UUID.randomUUID(), UUID.randomUUID(), "Shop", null, Set.of());

    @Test void enrichesOnlyTheOwnedPageWithCanonicalStatusesInOneBulkLookup() {
        UUID id = UUID.randomUUID();
        var booking = mock(BookingSnapshot.class);
        when(booking.id()).thenReturn(id);
        when(bookings.merchantOrders(merchant, 2, 20)).thenReturn(new PageImpl<>(List.of(booking), PageRequest.of(2, 20), 105));
        when(lifecycles.summaries(List.of(id))).thenReturn(Map.of(id,
                new LifecycleSnapshot(UUID.randomUUID(), id, null, ParcelStatus.IN_TRANSIT, Instant.EPOCH, List.of())));

        var result = service.orders(merchant, 2, 20);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(6);
        assertThat(result.totalElements()).isEqualTo(105);
        assertThat(result.items().getFirst().deliveryStatus()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(result.items().getFirst().statusUpdatedAt()).isEqualTo(Instant.EPOCH);
        verify(lifecycles).summaries(List.of(id));
    }

    @Test void doesNotInventCreatedStatusForLegacyBookingsWithoutALifecycle() {
        UUID id = UUID.randomUUID();
        var booking = mock(BookingSnapshot.class);
        when(bookings.merchantOrder(merchant, id)).thenReturn(booking);
        when(lifecycles.summaries(List.of(id))).thenReturn(Map.of());
        assertThat(service.order(merchant, id).deliveryStatus()).isNull();
    }

    @Test void checksOwnershipBeforeLookingUpDeliveryState() {
        UUID id = UUID.randomUUID();
        when(bookings.merchantOrder(merchant, id)).thenThrow(new AccessDeniedException("Access denied"));
        assertThatThrownBy(() -> service.order(merchant, id)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(lifecycles);
    }
}
