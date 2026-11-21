package com.swiftdrop.service.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.dto.execution.*;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.dto.booking.BookingSnapshot;
import com.swiftdrop.entity.execution.*;
import com.swiftdrop.exception.LifecycleConflictException;
import com.swiftdrop.service.booking.BookingReadService;
import com.swiftdrop.service.dispatch.DispatchCompletionService;
import com.swiftdrop.repository.execution.*;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParcelLifecycleService {
    private final IdentityAccountService identities;
    private final ParcelLifecycleRepository lifecycles;
    private final ParcelStatusHistoryRepository history;
    private final DeliveryProofRepository proofs;
    private final OutboxEventRepository outbox;
    private final BookingReadService bookings;
    private final DispatchCompletionService dispatch;
    private final ObjectMapper objectMapper;

    public ParcelLifecycleService(IdentityAccountService identities, ParcelLifecycleRepository lifecycles,
            ParcelStatusHistoryRepository history, DeliveryProofRepository proofs, OutboxEventRepository outbox,
            BookingReadService bookings, ObjectMapper objectMapper, DispatchCompletionService dispatch) {
        this.identities = identities; this.lifecycles = lifecycles; this.history = history;
        this.proofs = proofs; this.outbox = outbox; this.bookings = bookings; this.objectMapper = objectMapper;
        this.dispatch=dispatch;
    }

    @Transactional(readOnly = true)
    public List<CourierDeliverySummary> assignedDeliveries() {
        var courier = identities.requireCurrentRole(IdentityRole.COURIER);
        return lifecycles.findAllByAssignedCourierIdOrderByUpdatedAtDesc(courier.userId()).stream()
                .map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public CourierDeliveryDetails details(UUID lifecycleId) {
        var courier = identities.requireCurrentRole(IdentityRole.COURIER);
        var lifecycle = lifecycles.findFirstByIdAndAssignedCourierId(lifecycleId, courier.userId())
                .orElseThrow(() -> new AccessDeniedException("Delivery is not assigned to this courier"));
        return details(lifecycle);
    }

    @Transactional
    public CourierDeliveryDetails update(UUID lifecycleId, StatusUpdateRequest request) {
        var courier = identities.requireCurrentRole(IdentityRole.COURIER);
        var lifecycle = lifecycles.findByIdAndAssignedCourierId(lifecycleId, courier.userId())
                .orElseThrow(() -> new AccessDeniedException("Delivery is not assigned to this courier"));
        if (lifecycle.getStatus() != request.expectedStatus()) {
            throw new LifecycleConflictException("The parcel status has changed. Refresh and try again.");
        }
        ParcelStatusTransitionValidator.validate(request.expectedStatus(), request.status(), request);
        var previous = lifecycle.getStatus();
        lifecycle.transition(request.status());
        lifecycles.saveAndFlush(lifecycle);
        var next = history.findAllByLifecycleIdOrderBySequenceNoAsc(lifecycleId).stream()
                .mapToLong(item -> item.getSequenceNo()).max().orElse(0) + 1;
        var statusHistory = history.saveAndFlush(ParcelStatusHistory.create(lifecycleId, next, previous,
                request.status(), courier.userId(), normalized(request.failureReason())));
        if (request.status() == ParcelStatus.DELIVERED) {
            proofs.save(DeliveryProof.create(statusHistory.getId(), request.recipientName().trim()));
            bookings.deliveryCompleted(lifecycle.getBookingId());
        }
        if (request.status() == ParcelStatus.DELIVERED || request.status() == ParcelStatus.FAILED) {
            dispatch.complete(lifecycle.getBookingId());
        }
        outbox.save(OutboxEvent.parcelStatusChanged(lifecycleId, eventPayload(lifecycle, request.status(), next)));
        return details(lifecycle);
    }

    /** M4 creates an unassigned lifecycle in its transaction; M5 later supplies the courier. */
    @Transactional
    public UUID initialize(UUID bookingId, UUID courierId, UUID actorUserId) {
        var existing = lifecycles.lockByBookingId(bookingId);
        if (existing.isPresent()) {
            var lifecycle = existing.get();
            if (lifecycle.getStatus() == ParcelStatus.DELIVERED || lifecycle.getStatus() == ParcelStatus.FAILED) {
                throw new LifecycleConflictException("Terminal deliveries cannot be reassigned.");
            }
            lifecycle.assignCourier(courierId);
            lifecycles.save(lifecycle);
            return lifecycle.getId();
        }
        var lifecycle = lifecycles.save(ParcelLifecycle.create(bookingId, courierId));
        history.save(ParcelStatusHistory.create(lifecycle.getId(), 1, null, ParcelStatus.CREATED,
                actorUserId, null));
        outbox.save(OutboxEvent.parcelStatusChanged(lifecycle.getId(), eventPayload(lifecycle, ParcelStatus.CREATED, 1)));
        return lifecycle.getId();
    }

    private CourierDeliverySummary summary(ParcelLifecycle lifecycle) {
        var booking = booking(lifecycle);
        return new CourierDeliverySummary(lifecycle.getId(), lifecycle.getBookingId(), booking.trackingNumber(),
                lifecycle.getStatus(), lifecycle.getUpdatedAt());
    }

    private CourierDeliveryDetails details(ParcelLifecycle lifecycle) {
        var booking = booking(lifecycle);
        var items = history.findAllByLifecycleIdOrderBySequenceNoAsc(lifecycle.getId()).stream()
                .map(item -> new StatusHistoryItem(item.getSequenceNo(), item.getPreviousStatus(),
                        item.getNewStatus(), item.getActorUserId(), item.getFailureReason(), item.getOccurredAt()))
                .toList();
        return new CourierDeliveryDetails(lifecycle.getId(), lifecycle.getBookingId(), booking.trackingNumber(),
                booking.customerId(), booking.pickupAddressSnapshot(), booking.dropoffAddressSnapshot(),
                booking.parcelDescription(), booking.parcelWeightGrams(), lifecycle.getStatus(),
                lifecycle.getUpdatedAt(), items,booking.recipientName(),booking.recipientPhone(),booking.senderName(),booking.senderPhone());
    }

    private BookingSnapshot booking(ParcelLifecycle lifecycle) {
        return bookings.find(lifecycle.getBookingId())
                .orElseThrow(() -> new LifecycleConflictException("The booking for this delivery is unavailable."));
    }

    private String eventPayload(ParcelLifecycle lifecycle, ParcelStatus next, long sequence) {
        try {
            var booking=booking(lifecycle);
            return objectMapper.writeValueAsString(new ParcelEvent(UUID.randomUUID(), "parcel.status.changed", 1,
                    java.time.Instant.now(), booking.merchantId(), lifecycle.getBookingId(), lifecycle.getId(),
                    booking.trackingNumber(), next.name(), sequence));
        } catch (JsonProcessingException error) {
            throw new LifecycleConflictException("Unable to create the parcel status event.");
        }
    }

    private static String normalized(String value) { return value == null ? null : value.trim(); }
}
