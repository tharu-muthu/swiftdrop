package com.swiftdrop.dto.pricing;

import java.util.UUID;

public record ZoneResponse(UUID zoneId, String name, boolean active) { }