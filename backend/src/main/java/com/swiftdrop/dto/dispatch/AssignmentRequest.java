package com.swiftdrop.dto.dispatch;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AssignmentRequest(
    @NotNull(message = "Booking ID is required") UUID bookingId,
    @NotNull(message = "Courier ID is required") UUID courierId
) {}