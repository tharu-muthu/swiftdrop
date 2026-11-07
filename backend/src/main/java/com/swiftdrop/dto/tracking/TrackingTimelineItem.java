package com.swiftdrop.dto.tracking;

import com.swiftdrop.entity.execution.ParcelStatus;
import java.time.Instant;

public record TrackingTimelineItem(long sequenceNo, ParcelStatus status, Instant occurredAt) { }
