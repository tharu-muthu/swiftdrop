package com.swiftdrop.dto.tracking;

import com.swiftdrop.entity.execution.ParcelStatus;
import java.time.Instant;
import java.util.List;

public record PublicTrackingResponse(String trackingNumber, ParcelStatus currentStatus,
        Instant lastUpdatedAt, List<TrackingTimelineItem> timeline) { }
