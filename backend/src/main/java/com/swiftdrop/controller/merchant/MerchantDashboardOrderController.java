package com.swiftdrop.controller.merchant;

import com.swiftdrop.dto.booking.*;
import com.swiftdrop.dto.pricing.*;
import com.swiftdrop.service.booking.BookingService;
import com.swiftdrop.service.merchant.MerchantAccessService;
import com.swiftdrop.service.pricing.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchant/dashboard/orders")
public class MerchantDashboardOrderController {
    private final BookingService bookings; private final MerchantAccessService access; private final PricingService pricing;
    public MerchantDashboardOrderController(BookingService bookings,MerchantAccessService access,PricingService pricing) { this.bookings=bookings; this.access=access; this.pricing=pricing; }
    @GetMapping public List<BookingSnapshot> list() { return bookings.merchantOrders(access.requireApprovedOwnerMerchant()); }
    @GetMapping("/{id}") public BookingSnapshot detail(@PathVariable UUID id) { return bookings.merchantOrder(access.requireApprovedOwnerMerchant(),id); }
    @PostMapping public BookingSnapshot create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody BookingCreateRequest request) { return bookings.createMerchant(access.requireApprovedOwnerMerchant(),key,request); }
    @PostMapping("/quote") public QuoteResponse quote(@Valid @RequestBody QuoteRequest request) { return pricing.createQuote(QuoteOwner.merchant(access.requireApprovedOwnerMerchant().merchantId()),request); }
}
