package com.swiftdrop.controller.merchant;

import com.swiftdrop.dto.booking.*;
import com.swiftdrop.dto.pricing.*;
import com.swiftdrop.service.booking.BookingService;
import com.swiftdrop.service.merchant.MerchantAccessService;
import com.swiftdrop.service.merchant.MerchantOrderQueryService;
import com.swiftdrop.dto.merchant.MerchantOrderPage;
import com.swiftdrop.dto.merchant.MerchantOrderResponse;
import com.swiftdrop.service.pricing.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchant/dashboard/orders")
public class MerchantDashboardOrderController {
    private final BookingService bookings; private final MerchantAccessService access; private final PricingService pricing;
    private final MerchantOrderQueryService queries;
    public MerchantDashboardOrderController(BookingService bookings,MerchantAccessService access,PricingService pricing,MerchantOrderQueryService queries) { this.bookings=bookings; this.access=access; this.pricing=pricing; this.queries=queries; }
    @GetMapping public MerchantOrderPage list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return queries.orders(access.requireApprovedOwnerMerchant(),page,size); }
    @GetMapping("/{id}") public MerchantOrderResponse detail(@PathVariable UUID id) { return queries.order(access.requireApprovedOwnerMerchant(),id); }
    @PostMapping public BookingSnapshot create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody BookingCreateRequest request) { return bookings.createMerchant(access.requireApprovedOwnerMerchant(),key,request); }
    @PostMapping("/quote") public QuoteResponse quote(@Valid @RequestBody QuoteRequest request) { return pricing.createQuote(QuoteOwner.merchant(access.requireApprovedOwnerMerchant().merchantId()),request); }
}
