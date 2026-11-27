package com.swiftdrop.dto.execution;

/** M6 read contract for customer counters, including bookings awaiting lifecycle creation. */
public record CustomerDeliveryCounts(long total, long pending, long inTransit, long delivered) { }
