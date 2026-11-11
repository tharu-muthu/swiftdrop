package com.swiftdrop.dto.dispatch;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReassignmentRequest(
    @NotNull(message = "Booking ID is required") UUID bookingId,
    @NotNull(message = "New courier ID is required") UUID newCourierId,
    @NotBlank(message = "Reason is required") String reason
) {}