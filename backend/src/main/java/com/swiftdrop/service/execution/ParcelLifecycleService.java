package com.swiftdrop.service.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.dto.execution.*;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.entity.booking.ParcelBooking;
import com.swiftdrop.entity.execution.*;
import com.swiftdrop.exception.LifecycleConflictException;
import com.swiftdrop.repository.booking.ParcelBookingRepository;
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
    private final ParcelBookingRepository bookings;
    private final ObjectMapper objectMapper;

    public ParcelLifecycleService(IdentityAccountService identities, ParcelLifecycleRepository lifecycles,
            ParcelStatusHistoryRepository history, DeliveryProofRepository proofs, OutboxEventRepository outbox,
            ParcelBookingRepository bookings, ObjectMapper objectMapper) {
        this.identities = identities; this.lifecycles = lifecycles; this.history = history;
        this.proofs = proofs; this.outbox = outbox; this.bookings = bookings; this.objectMapper = objectMapper;
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
        var lifecycle = lifecycles.findByIdAndAssignedCourierId(lifecycleId, courier.userId())
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
        }
        outbox.save(OutboxEvent.parcelStatusChanged(lifecycleId, eventPayload(lifecycle, previous, request.status())));
        return details(lifecycle);
    }

    /** M4 calls this in its booking transaction after M5 supplies the assigned courier. */
    @Transactional
    public UUID initialize(UUID bookingId, UUID courierId, UUID actorUserId) {
        if (lifecycles.existsByBookingId(bookingId)) {
            throw new LifecycleConflictException("A lifecycle already exists for this booking.");
        }
        var lifecycle = lifecycles.save(ParcelLifecycle.create(bookingId, courierId));
        history.save(ParcelStatusHistory.create(lifecycle.getId(), 1, null, ParcelStatus.CREATED,
                actorUserId, null));
        return lifecycle.getId();
    }

    private CourierDeliverySummary summary(ParcelLifecycle lifecycle) {
        var booking = booking(lifecycle);
        return new CourierDeliverySummary(lifecycle.getId(), lifecycle.getBookingId(), booking.getTrackingNumber(),
                lifecycle.getStatus(), lifecycle.getUpdatedAt());
    }

    private CourierDeliveryDetails details(ParcelLifecycle lifecycle) {
        var booking = booking(lifecycle);
        var items = history.findAllByLifecycleIdOrderBySequenceNoAsc(lifecycle.getId()).stream()
                .map(item -> new StatusHistoryItem(item.getSequenceNo(), item.getPreviousStatus(),
                        item.getNewStatus(), item.getActorUserId(), item.getFailureReason(), item.getOccurredAt()))
                .toList();
        return new CourierDeliveryDetails(lifecycle.getId(), lifecycle.getBookingId(), booking.getTrackingNumber(),
                booking.getCustomerId(), booking.getPickupAddressSnapshot(), booking.getDropoffAddressSnapshot(),
                booking.getParcelDescription(), booking.getParcelWeightGrams(), lifecycle.getStatus(),
                lifecycle.getUpdatedAt(), items);
    }

    private ParcelBooking booking(ParcelLifecycle lifecycle) {
        return bookings.findById(lifecycle.getBookingId())
                .orElseThrow(() -> new LifecycleConflictException("The booking for this delivery is unavailable."));
    }

    private String eventPayload(ParcelLifecycle lifecycle, ParcelStatus previous, ParcelStatus next) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "eventId", UUID.randomUUID(), "lifecycleId", lifecycle.getId(),
                    "bookingId", lifecycle.getBookingId(), "previousStatus", previous,
                    "newStatus", next, "occurredAt", java.time.Instant.now()));
        } catch (JsonProcessingException error) {
            throw new LifecycleConflictException("Unable to create the parcel status event.");
        }
    }

    private static String normalized(String value) { return value == null ? null : value.trim(); }
}
