package com.swiftdrop.dto.booking;

import java.util.List;

public record BookingHistoryResponse(List<BookingHistoryItem> bookings, BookingHistorySummary summary,
        int page, int size, int totalPages, long totalElements) { }
