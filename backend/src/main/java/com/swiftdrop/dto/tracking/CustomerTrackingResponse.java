package com.swiftdrop.dto.tracking;

import com.swiftdrop.entity.execution.ParcelStatus;
import java.time.Instant;
import java.util.List;

public record CustomerTrackingResponse(String trackingNumber, ParcelStatus currentStatus,
        Instant lastUpdatedAt, List<TrackingTimelineItem> timeline, String pickupCity,
        String deliveryCity, String recipientName) { }
