package com.swiftdrop.entity.webhook;

import com.swiftdrop.entity.identity.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "webhook_deliveries", schema = "swiftdrop")
public class WebhookDelivery extends UuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private WebhookSubscription subscription;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(name = "payload_body", nullable = false, updatable = false, columnDefinition = "text")
    private String payloadBody;

    @Column(name = "delivery_state", nullable = false, length = 24)
    private String deliveryState;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected WebhookDelivery() { }

    public static WebhookDelivery create(WebhookSubscription subscription, UUID eventId,
            String eventType, String payloadBody) {
        var delivery = new WebhookDelivery();
        delivery.subscription = subscription;
        delivery.eventId = eventId;
        delivery.eventType = eventType;
        delivery.payloadBody = payloadBody;
        delivery.deliveryState = "PENDING";
        delivery.attemptCount = 0;
        return delivery;
    }

    public void recordOutcome(String state, int attemptCount, Integer httpStatus, String error,
            Instant completedAt) {
        this.deliveryState = state;
        this.attemptCount = attemptCount;
        this.lastHttpStatus = httpStatus;
        this.lastError = error;
        this.completedAt = completedAt;
    }

    public UUID getId() { return super.getId(); }
    public WebhookSubscription getSubscription() { return subscription; }
    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getPayloadBody() { return payloadBody; }
    public String getDeliveryState() { return deliveryState; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Integer getLastHttpStatus() { return lastHttpStatus; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
