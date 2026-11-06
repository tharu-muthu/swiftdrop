package com.swiftdrop.dto.booking;

import java.time.Instant;
import java.util.UUID;

public record BookingHistoryItem(UUID id, String trackingNumber, String recipientName, String recipientPhone,
        String pickupCity, String deliveryCity, Instant bookedAt, Integer deliveryFeeMinor,
        String currencyCode, String bookingState) { }
