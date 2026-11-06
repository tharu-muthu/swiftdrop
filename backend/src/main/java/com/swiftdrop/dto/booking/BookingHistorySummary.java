package com.swiftdrop.dto.booking;

/** M4 aggregates. In-transit/delivered remain unavailable until M6 supplies a lifecycle projection. */
public record BookingHistorySummary(long totalBookings, long pendingBookings, boolean deliveryStatusAvailable) { }
