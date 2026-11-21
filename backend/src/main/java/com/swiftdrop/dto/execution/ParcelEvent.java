package com.swiftdrop.dto.execution;

import java.time.Instant;
import java.util.UUID;

/** Versioned, public-safe integration envelope. Event ID is persisted in the immutable outbox body. */
public record ParcelEvent(UUID eventId, String eventType, int schemaVersion, Instant occurredAt,
        UUID merchantId, UUID parcelId, UUID lifecycleId, String trackingNumber, String status, long sequence) {
    public ParcelEvent {
        if(eventId==null||parcelId==null||lifecycleId==null||occurredAt==null||schemaVersion!=1||sequence<1
                ||!"parcel.status.changed".equals(eventType)||trackingNumber==null||!trackingNumber.matches("SDP-[A-Z0-9]{6,32}"))
            throw new IllegalArgumentException("Invalid parcel event envelope");
        com.swiftdrop.entity.execution.ParcelStatus.valueOf(status);
    }
}
