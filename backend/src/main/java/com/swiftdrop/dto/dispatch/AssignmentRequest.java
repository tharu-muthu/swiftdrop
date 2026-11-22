package com.swiftdrop.dto.dispatch;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AssignmentRequest(
    @NotNull(message = "Booking ID is required") UUID bookingId,
    @NotNull(message = "Courier ID is required") UUID courierId
) {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Only documented request fields are accepted.");
    }
}