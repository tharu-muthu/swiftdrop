package com.swiftdrop.dto.execution;

import com.swiftdrop.entity.execution.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

public record StatusHistoryItem(long sequenceNo, ParcelStatus previousStatus, ParcelStatus newStatus,
        UUID actorUserId, String failureReason, Instant occurredAt) { }
