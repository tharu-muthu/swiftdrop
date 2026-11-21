package com.swiftdrop.controller.booking;

import com.swiftdrop.dto.booking.BookingSnapshot;
import com.swiftdrop.service.booking.ReceiptService;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReceiptController {
    private final ReceiptService receipts;
    public ReceiptController(ReceiptService receipts) { this.receipts=receipts; }
    @GetMapping("/api/v1/bookings/{id}/receipt") public ResponseEntity<BookingSnapshot> receipt(@PathVariable UUID id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(receipts.receipt(id)); }
}
