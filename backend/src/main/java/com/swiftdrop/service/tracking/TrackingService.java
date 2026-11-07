package com.swiftdrop.service.tracking;

import com.swiftdrop.dto.tracking.PublicTrackingResponse;
import com.swiftdrop.dto.tracking.CustomerTrackingResponse;
import com.swiftdrop.dto.tracking.TrackingTimelineItem;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.entity.booking.ParcelBooking;
import com.swiftdrop.entity.execution.ParcelLifecycle;
import com.swiftdrop.repository.tracking.TrackingBookingRepository;
import com.swiftdrop.repository.tracking.TrackingLifecycleRepository;
import com.swiftdrop.repository.tracking.TrackingStatusHistoryRepository;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TrackingService {
    private final TrackingBookingRepository bookings;
    private final TrackingLifecycleRepository lifecycles;
    private final TrackingStatusHistoryRepository history;
    private final IdentityAccountService identities;

    public TrackingService(TrackingBookingRepository bookings, TrackingLifecycleRepository lifecycles,
            TrackingStatusHistoryRepository history, IdentityAccountService identities) {
        this.bookings = bookings;
        this.lifecycles = lifecycles;
        this.history = history;
        this.identities = identities;
    }

    public Optional<PublicTrackingResponse> publicTracking(String rawTrackingNumber) {
        var trackingNumber = normalize(rawTrackingNumber);
        if (trackingNumber == null) return Optional.empty();

        return bookings.findByTrackingNumber(trackingNumber)
                .flatMap(booking -> trackingFor(booking));
    }

    public Optional<CustomerTrackingResponse> customerTracking(String rawTrackingNumber) {
        var customer = identities.requireCurrentRole(IdentityRole.CUSTOMER);
        var trackingNumber = normalize(rawTrackingNumber);
        if (trackingNumber == null) return Optional.empty();

        return bookings.findByTrackingNumber(trackingNumber)
                .filter(booking -> booking.getCustomerId().equals(customer.userId()))
                .flatMap(this::customerTrackingFor);
    }

    private Optional<PublicTrackingResponse> trackingFor(ParcelBooking booking) {
        return lifecycles.findByBookingId(booking.getId())
                .map(lifecycle -> response(booking, lifecycle));
    }

    private PublicTrackingResponse response(ParcelBooking booking, ParcelLifecycle lifecycle) {
        var timeline = history.findAllByLifecycleIdOrderBySequenceNoAsc(lifecycle.getId()).stream()
                .map(item -> new TrackingTimelineItem(item.getSequenceNo(), item.getNewStatus(),
                        item.getOccurredAt()))
                .toList();
        return new PublicTrackingResponse(booking.getTrackingNumber(), lifecycle.getStatus(),
                lifecycle.getUpdatedAt(), timeline);
    }

    private Optional<CustomerTrackingResponse> customerTrackingFor(ParcelBooking booking) {
        return lifecycles.findByBookingId(booking.getId())
                .map(lifecycle -> {
                    var timeline = history.findAllByLifecycleIdOrderBySequenceNoAsc(lifecycle.getId()).stream()
                            .map(item -> new TrackingTimelineItem(item.getSequenceNo(), item.getNewStatus(),
                                    item.getOccurredAt()))
                            .toList();
                    return new CustomerTrackingResponse(booking.getTrackingNumber(), lifecycle.getStatus(),
                            lifecycle.getUpdatedAt(), timeline, booking.getPickupCity(), booking.getDeliveryCity(),
                            booking.getRecipientName());
                });
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
