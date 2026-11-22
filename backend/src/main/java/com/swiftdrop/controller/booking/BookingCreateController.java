package com.swiftdrop.controller.booking;

import com.swiftdrop.dto.booking.*;
import com.swiftdrop.service.booking.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingCreateController {
    private final BookingService bookings;
    public BookingCreateController(BookingService bookings) { this.bookings=bookings; }
    @PostMapping
    public ResponseEntity<BookingSnapshot> create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody BookingCreateRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(bookings.createCustomer(key,request));
    }
}
