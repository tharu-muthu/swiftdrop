package com.swiftdrop.controller.merchant;

import com.swiftdrop.dto.booking.*;
import com.swiftdrop.service.booking.BookingService;
import com.swiftdrop.service.merchant.MerchantAccessService;
import com.swiftdrop.service.merchant.MerchantOrderQueryService;
import com.swiftdrop.dto.merchant.MerchantOrderResponse;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchant/orders")
public class MerchantOrderController {
    private final BookingService bookings;
    private final MerchantAccessService merchants;
    private final MerchantOrderQueryService queries;
    public MerchantOrderController(BookingService bookings,MerchantAccessService merchants,MerchantOrderQueryService queries) { this.bookings=bookings; this.merchants=merchants; this.queries=queries; }
    @PostMapping
    public BookingSnapshot create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody BookingCreateRequest request) {
        return bookings.createMerchant(merchants.requireCurrentApiMerchant(),key,request);
    }
    @GetMapping public org.springframework.http.ResponseEntity<List<MerchantOrderResponse>> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="100") int size) {
        var result=queries.orders(merchants.requireCurrentApiMerchant(),page,size);
        // Retain the partner API's array body; expose page metadata through headers.
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .header("X-Total-Count",Long.toString(result.totalElements())).header("X-Total-Pages",Integer.toString(result.totalPages()))
                .header("X-Page",Integer.toString(result.page())).header("X-Page-Size",Integer.toString(result.size())).body(result.items());
    }
    @GetMapping("/{id}") public MerchantOrderResponse detail(@PathVariable UUID id) { return queries.order(merchants.requireCurrentApiMerchant(),id); }
}
