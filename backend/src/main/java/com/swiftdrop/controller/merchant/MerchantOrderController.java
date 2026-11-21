package com.swiftdrop.controller.merchant;

import com.swiftdrop.dto.booking.*;
import com.swiftdrop.service.booking.BookingService;
import com.swiftdrop.service.merchant.MerchantAccessService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchant/orders")
public class MerchantOrderController {
    private final BookingService bookings;
    private final MerchantAccessService merchants;
    public MerchantOrderController(BookingService bookings,MerchantAccessService merchants) { this.bookings=bookings; this.merchants=merchants; }
    @PostMapping
    public BookingSnapshot create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody BookingCreateRequest request) {
        return bookings.createMerchant(merchants.requireCurrentApiMerchant(),key,request);
    }
    @GetMapping public List<BookingSnapshot> list() { return bookings.merchantOrders(merchants.requireCurrentApiMerchant()); }
    @GetMapping("/{id}") public BookingSnapshot detail(@PathVariable UUID id) { return bookings.merchantOrder(merchants.requireCurrentApiMerchant(),id); }
}
