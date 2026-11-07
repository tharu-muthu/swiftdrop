package com.swiftdrop.controller.booking;

import com.swiftdrop.dto.booking.*;
import com.swiftdrop.service.booking.BookingHistoryService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@Validated
public class BookingHistoryController {
    private final BookingHistoryService history;
    public BookingHistoryController(BookingHistoryService history) { this.history = history; }

    @GetMapping("/api/v1/bookings")
    public ResponseEntity<BookingHistoryResponse> history(
            @RequestParam(required = false) String search, @RequestParam(required = false) String state,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.history(search, state, from, to, page, size));
    }

    @GetMapping("/api/v1/bookings/{bookingId}")
    public ResponseEntity<BookingHistoryItem> detail(@PathVariable UUID bookingId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.detail(bookingId));
    }
}
