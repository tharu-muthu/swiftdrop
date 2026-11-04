package com.swiftdrop.entity.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import java.util.UUID;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;

@Entity
@Table(name = "identity_audit_events", schema = "swiftdrop")
@Immutable
public class IdentityAuditEvent extends UuidEntity {
    @Column(name = "actor_kind", nullable = false, length = 16)
    private String actorKind;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false, length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> beforeState = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> afterState = Map.of();

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Generated(event = EventType.INSERT)
    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    private Instant occurredAt;

    protected IdentityAuditEvent() { }

    public String getActorKind() { return actorKind; }
    public UUID getActorUserId() { return actorUserId; }
    public UUID getTargetUserId() { return targetUserId; }
    public String getAction() { return action; }
    public String getReason() { return reason; }
    public Map<String, Object> getBeforeState() { return beforeState; }
    public Map<String, Object> getAfterState() { return afterState; }
    public String getCorrelationId() { return correlationId; }
    public Instant getOccurredAt() { return occurredAt; }
}
