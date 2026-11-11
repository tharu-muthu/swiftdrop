package com.swiftdrop.dto.dispatch;

import java.util.UUID;

public record AssignmentResponse(
    UUID assignmentId, 
    UUID bookingId, 
    UUID courierId
) {}