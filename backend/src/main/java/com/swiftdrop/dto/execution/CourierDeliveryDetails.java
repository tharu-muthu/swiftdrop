package com.swiftdrop.dto.execution;

import com.swiftdrop.entity.execution.ParcelStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CourierDeliveryDetails(UUID lifecycleId, UUID bookingId, String trackingNumber,
        UUID recipientAccountId, String pickupAddress, String dropoffAddress, String parcelDescription,
        int parcelWeightGrams, ParcelStatus status, Instant updatedAt, List<StatusHistoryItem> history) { }
