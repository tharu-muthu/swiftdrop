package com.swiftdrop.dto.booking;

/** M4 aggregates enriched through the M6 lifecycle read contract. */
public record BookingHistorySummary(long totalBookings, long pendingBookings, boolean deliveryStatusAvailable,
        long inTransitBookings,long deliveredBookings) { }
