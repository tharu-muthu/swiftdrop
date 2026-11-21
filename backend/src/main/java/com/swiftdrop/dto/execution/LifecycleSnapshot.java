package com.swiftdrop.dto.execution;

import com.swiftdrop.entity.execution.ParcelStatus;
import java.time.Instant;
import java.util.*;

public record LifecycleSnapshot(UUID id,UUID bookingId,UUID courierId,ParcelStatus status,Instant updatedAt,List<StatusHistoryItem> history) { }
