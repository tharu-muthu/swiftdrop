package com.swiftdrop.dto.execution;

import com.swiftdrop.entity.execution.ParcelStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotNull ParcelStatus expectedStatus,
        @NotNull ParcelStatus status,
        @Size(max = 200) String recipientName,
        @Size(max = 500) String failureReason) { }
