package com.swiftdrop.entity.webhook;

import com.swiftdrop.entity.identity.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "webhook_delivery_attempts", schema = "swiftdrop")
public class WebhookDeliveryAttempt extends UuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private WebhookDelivery delivery;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "started_at", nullable = false, insertable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "outcome", nullable = false, length = 24)
    private String outcome;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    protected WebhookDeliveryAttempt() { }

    public WebhookDelivery getDelivery() { return delivery; }
    public int getAttemptNumber() { return attemptNumber; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getOutcome() { return outcome; }
    public String getErrorMessage() { return errorMessage; }
}
