package com.swiftdrop.controller.booking;

import com.swiftdrop.dto.booking.BookingDashboardSummary;
import com.swiftdrop.service.booking.BookingDashboardService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingDashboardController {
    private final BookingDashboardService dashboard;

    public BookingDashboardController(BookingDashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping("/api/v1/bookings/dashboard")
    public ResponseEntity<BookingDashboardSummary> summary() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dashboard.summary());
    }
}
