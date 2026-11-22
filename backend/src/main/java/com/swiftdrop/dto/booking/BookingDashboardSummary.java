package com.swiftdrop.dto.booking;

/** Customer dashboard figures assembled from booking ownership and M6 lifecycle state. */
public record BookingDashboardSummary(long activeBookings, long deliveredBookings,
        long pendingReceipts, long thisMonthShipments) { }
