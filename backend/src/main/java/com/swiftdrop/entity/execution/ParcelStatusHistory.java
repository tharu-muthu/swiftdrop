package com.swiftdrop.entity.execution;

import com.swiftdrop.entity.identity.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import java.time.Instant;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "parcel_status_history", schema = "swiftdrop")
public class ParcelStatusHistory extends UuidEntity {
    @Column(name = "lifecycle_id", nullable = false, updatable = false)
    private UUID lifecycleId;
    @Column(name = "sequence_no", nullable = false, updatable = false)
    private long sequenceNo;
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 24, updatable = false)
    private ParcelStatus previousStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 24, updatable = false)
    private ParcelStatus newStatus;
    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;
    @Column(name = "failure_reason", length = 500, updatable = false)
    private String failureReason;
    @Generated(event = EventType.INSERT)
    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    private Instant occurredAt;

    protected ParcelStatusHistory() { }
    public static ParcelStatusHistory create(UUID lifecycleId, long sequenceNo, ParcelStatus previous,
            ParcelStatus next, UUID actorUserId, String failureReason) {
        var history = new ParcelStatusHistory();
        history.lifecycleId = lifecycleId; history.sequenceNo = sequenceNo;
        history.previousStatus = previous; history.newStatus = next;
        history.actorUserId = actorUserId; history.failureReason = failureReason;
        return history;
    }
    public UUID getLifecycleId() { return lifecycleId; }
    public long getSequenceNo() { return sequenceNo; }
    public ParcelStatus getPreviousStatus() { return previousStatus; }
    public ParcelStatus getNewStatus() { return newStatus; }
    public UUID getActorUserId() { return actorUserId; }
    public String getFailureReason() { return failureReason; }
    public Instant getOccurredAt() { return occurredAt; }
}
