package com.swiftdrop.service.merchant;

import com.swiftdrop.dto.booking.BookingSnapshot;
import com.swiftdrop.dto.execution.LifecycleSnapshot;
import com.swiftdrop.dto.merchant.*;
import com.swiftdrop.service.booking.BookingService;
import com.swiftdrop.service.execution.LifecycleReadService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** M2 combines owned booking pages with the canonical M6 status read contract. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class MerchantOrderQueryService {
    private final BookingService bookings;
    private final LifecycleReadService lifecycles;

    public MerchantOrderQueryService(BookingService bookings, LifecycleReadService lifecycles) {
        this.bookings = bookings;
        this.lifecycles = lifecycles;
    }

    public MerchantOrderPage orders(MerchantContext merchant, int page, int size) {
        var owned = bookings.merchantOrders(merchant, page, size);
        var states = lifecycles.summaries(owned.getContent().stream().map(BookingSnapshot::id).toList());
        var items = owned.getContent().stream().map(booking -> response(booking, states.get(booking.id()))).toList();
        return new MerchantOrderPage(items, owned.getNumber(), owned.getSize(), owned.getTotalPages(), owned.getTotalElements());
    }

    public MerchantOrderResponse order(MerchantContext merchant, UUID id) {
        var owned = bookings.merchantOrder(merchant, id);
        return response(owned, lifecycles.summaries(List.of(id)).get(id));
    }

    private static MerchantOrderResponse response(BookingSnapshot booking, LifecycleSnapshot lifecycle) {
        return new MerchantOrderResponse(booking, lifecycle == null ? null : lifecycle.status(),
                lifecycle == null ? null : lifecycle.updatedAt());
    }
}
