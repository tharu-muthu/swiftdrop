package com.swiftdrop.service.tracking;

import com.swiftdrop.dto.tracking.*;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.service.booking.BookingReadService;
import com.swiftdrop.service.execution.LifecycleReadService;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class TrackingService {
    private final BookingReadService bookings;
    private final LifecycleReadService lifecycles;
    private final IdentityAccountService identities;
    public TrackingService(BookingReadService bookings,LifecycleReadService lifecycles,IdentityAccountService identities) {
        this.bookings=bookings; this.lifecycles=lifecycles; this.identities=identities;
    }
    public Optional<PublicTrackingResponse> publicTracking(String raw) {
        var number=normalize(raw); if(number==null) return Optional.empty();
        return bookings.byTrackingNumber(number).flatMap(booking->lifecycles.find(booking.id()).map(lifecycle->
            new PublicTrackingResponse(booking.trackingNumber(),lifecycle.status(),lifecycle.updatedAt(),
                lifecycle.history().stream().map(item->new TrackingTimelineItem(item.sequenceNo(),item.newStatus(),item.occurredAt())).toList(), lifecycle.courierId() != null)));
    }
    public Optional<CustomerTrackingResponse> customerTracking(String raw) {
        var customer=identities.requireCurrentRole(IdentityRole.CUSTOMER);
        var number=normalize(raw); if(number==null) return Optional.empty();
        return bookings.byTrackingNumber(number).filter(booking->customer.userId().equals(booking.customerId()))
            .flatMap(booking->lifecycles.find(booking.id()).map(lifecycle->new CustomerTrackingResponse(booking.trackingNumber(),lifecycle.status(),lifecycle.updatedAt(),
                lifecycle.history().stream().map(item->new TrackingTimelineItem(item.sequenceNo(),item.newStatus(),item.occurredAt())).toList(),
                booking.pickupCity(),booking.deliveryCity(),booking.recipientName(), lifecycle.courierId() != null)));
    }
    public static String normalize(String raw) {
        if(raw==null) return null;
        var number=raw.trim().toUpperCase(java.util.Locale.ROOT);
        return number.matches("SDP-[A-Z0-9]{6,32}")?number:null;
    }
}
