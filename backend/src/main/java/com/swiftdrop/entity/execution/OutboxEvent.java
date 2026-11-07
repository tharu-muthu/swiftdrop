package com.swiftdrop.entity.execution;

import com.swiftdrop.entity.identity.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events", schema = "swiftdrop")
public class OutboxEvent extends UuidEntity {
    @Column(name = "event_type", nullable = false, length = 100, updatable = false)
    private String eventType;
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private String payload;
    @Column(nullable = false)
    private int attempts;
    @Generated(event = EventType.INSERT)
    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    private Instant occurredAt;
    protected OutboxEvent() { }
    public static OutboxEvent parcelStatusChanged(UUID lifecycleId, String payload) {
        var event = new OutboxEvent();
        event.eventType = "ParcelStatusChanged"; event.aggregateId = lifecycleId;
        event.payload = payload;
        return event;
    }
}
