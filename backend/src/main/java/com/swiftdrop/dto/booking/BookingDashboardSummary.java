package com.swiftdrop.dto.booking;

/** Values owned by M4. Delivery totals are supplied later by the M6 projection contract. */
public record BookingDashboardSummary(long activeBookings, long pendingReceipts, long thisMonthShipments) { }
