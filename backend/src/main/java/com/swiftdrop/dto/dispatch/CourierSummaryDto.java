package com.swiftdrop.dto.dispatch;

import java.util.UUID;

public record CourierSummaryDto(
    UUID courierId,
    String vehicleType,
    String licensePlate,
    boolean active
) {}
