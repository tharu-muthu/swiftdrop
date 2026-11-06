package com.swiftdrop.dto.webhook;

import java.time.Instant;
import java.util.UUID;

/** Immutable event envelope supplied by the M6 committed-event adapter. */
public record WebhookEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID merchantId,
        UUID parcelId,
        String trackingNumber,
        String status,
        long sequence) {

    public WebhookEvent {
        if (eventId == null || merchantId == null || parcelId == null || occurredAt == null) {
            throw new IllegalArgumentException("Webhook event identifiers and occurredAt are required.");
        }
        if (eventType == null || eventType.isBlank() || eventType.length() > 100) {
            throw new IllegalArgumentException("Webhook eventType is required.");
        }
        if (schemaVersion < 1 || sequence < 0) {
            throw new IllegalArgumentException("Webhook event version and sequence are invalid.");
        }
        if (trackingNumber == null || trackingNumber.isBlank() || trackingNumber.length() > 40
                || status == null || status.isBlank() || status.length() > 40) {
            throw new IllegalArgumentException("Webhook trackingNumber and status are required.");
        }
    }
}
