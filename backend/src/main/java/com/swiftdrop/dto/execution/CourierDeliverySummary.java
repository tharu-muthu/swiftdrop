package com.swiftdrop.dto.execution;

import com.swiftdrop.entity.execution.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

public record CourierDeliverySummary(UUID lifecycleId, UUID bookingId, String trackingNumber,
        ParcelStatus status, Instant updatedAt) { }
