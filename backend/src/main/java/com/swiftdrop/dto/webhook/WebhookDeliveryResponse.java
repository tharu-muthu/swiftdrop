package com.swiftdrop.dto.webhook;

import com.swiftdrop.entity.webhook.WebhookDelivery;
import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryResponse(UUID id, UUID eventId, String eventType, String state,
        int attemptCount, Integer lastHttpStatus, String lastError, Instant createdAt, Instant completedAt) {
    public static WebhookDeliveryResponse from(WebhookDelivery delivery) {
        return new WebhookDeliveryResponse(delivery.getId(), delivery.getEventId(), delivery.getEventType(),
                delivery.getDeliveryState(), delivery.getAttemptCount(), delivery.getLastHttpStatus(),
                delivery.getLastError(), delivery.getCreatedAt(), delivery.getCompletedAt());
    }
}
