package com.swiftdrop.service.booking;

import com.swiftdrop.dto.booking.BookingDashboardSummary;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.repository.booking.ParcelBookingRepository;
import com.swiftdrop.service.identity.IdentityAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingDashboardService {
    private final IdentityAccountService identities;
    private final ParcelBookingRepository bookings;
    private final com.swiftdrop.service.execution.LifecycleReadService lifecycles;

    public BookingDashboardService(IdentityAccountService identities, ParcelBookingRepository bookings,com.swiftdrop.service.execution.LifecycleReadService lifecycles) {
        this.identities = identities;
        this.bookings = bookings;
        this.lifecycles=lifecycles;
    }

    public BookingDashboardSummary summary() {
        var customer = identities.requireCurrentRole(IdentityRole.CUSTOMER);
        var aggregate = bookings.dashboardForCustomer(customer.userId());
        var all=bookings.findHistory(customer.userId(),null,null,null,null,org.springframework.data.domain.Pageable.unpaged()).getContent();
        var statuses=lifecycles.statuses(all.stream().map(row->row.getId()).toList());
        long active=all.stream().filter(row->!"CANCELLED".equals(row.getBookingState()))
                .filter(row->!java.util.Set.of("DELIVERED","FAILED").contains(statuses.getOrDefault(row.getId(),"CREATED"))).count();
        long delivered=all.stream().filter(row -> "DELIVERED".equals(statuses.get(row.getId()))).count();
        return new BookingDashboardSummary(active, delivered, aggregate.getPendingReceipts(),
                aggregate.getThisMonthShipments());
    }
}
