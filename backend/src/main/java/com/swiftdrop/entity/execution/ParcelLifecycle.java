package com.swiftdrop.entity.execution;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.swiftdrop.entity.identity.UuidEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "parcel_lifecycles", schema = "swiftdrop")
public class ParcelLifecycle extends UuidEntity {
    @Column(name = "booking_id", nullable = false, updatable = false, unique = true)
    private UUID bookingId;

    // Fixed: Removed nullable = false to support unassigned states
    @Column(name = "assigned_courier_id")
    private UUID assignedCourierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ParcelStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected ParcelLifecycle() { }

    public UUID getBookingId() { return bookingId; }
    public UUID getAssignedCourierId() { return assignedCourierId; }
    public ParcelStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static ParcelLifecycle create(UUID bookingId, UUID courierId) {
        var lifecycle = new ParcelLifecycle();
        lifecycle.bookingId = bookingId;
        lifecycle.assignedCourierId = courierId;
        lifecycle.status = ParcelStatus.CREATED;
        return lifecycle;
    }
    
    public void transition(ParcelStatus next) { status = next; }
    public void assignCourier(UUID courierId) { this.assignedCourierId = courierId; }
}