package com.swiftdrop.service.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.dto.booking.*;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.dto.merchant.MerchantContext;
import com.swiftdrop.dto.pricing.QuoteRequest;
import com.swiftdrop.entity.booking.ParcelBooking;
import com.swiftdrop.entity.pricing.ParcelSize;
import com.swiftdrop.exception.LifecycleConflictException;
import com.swiftdrop.repository.booking.ParcelBookingRepository;
import com.swiftdrop.service.execution.ParcelLifecycleService;
import com.swiftdrop.service.identity.IdentityAccountService;
import com.swiftdrop.service.pricing.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private final ParcelBookingRepository bookings;
    private final PricingService pricing;
    private final ParcelLifecycleService execution;
    private final IdentityAccountService identities;
    private final ObjectMapper json;
    public BookingService(ParcelBookingRepository bookings, PricingService pricing, ParcelLifecycleService execution,
            IdentityAccountService identities, ObjectMapper json) {
        this.bookings=bookings; this.pricing=pricing; this.execution=execution; this.identities=identities; this.json=json;
    }
    @Transactional
    public BookingSnapshot createCustomer(String key, BookingCreateRequest request) {
        var user=identities.requireCurrentRole(IdentityRole.CUSTOMER);
        return create(QuoteOwner.customer(user.userId()),user.userId(),key,request);
    }
    @Transactional
    public BookingSnapshot createMerchant(MerchantContext merchant,String key,BookingCreateRequest request) {
        return create(QuoteOwner.merchant(merchant.merchantId()),merchant.ownerUserId(),key,request);
    }
    private BookingSnapshot create(QuoteOwner owner,UUID actor,String key,BookingCreateRequest request) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,100}")) throw new IllegalArgumentException("Invalid idempotency key");
        if(!owner.merchant()&&request.externalReference()!=null) throw new IllegalArgumentException("External references belong to merchant orders");
        bookings.lockRequest(owner.id()+":"+key);
        var hash=hash(request);
        var existing=owner.merchant()?bookings.findByMerchantIdAndIdempotencyKey(owner.id(),key):bookings.findByCustomerIdAndIdempotencyKey(owner.id(),key);
        if(existing.isPresent()) {
            if(!hash.equals(existing.get().getRequestHash())) throw new LifecycleConflictException("This request key was already used with different details.");
            return BookingReadService.snapshot(existing.get());
        }
        // A quotation is a single booking commitment, even across different request keys.
        bookings.lockRequest("quote:"+request.quoteId());
        var quote=pricing.validateForBooking(request.quoteId(),owner,new QuoteRequest(request.pickup().postalCode(),
                request.recipient().postalCode(),request.parcel().weightGrams(),ParcelSize.valueOf(request.parcel().size())));
        if(quote.amountMinor()>Integer.MAX_VALUE) throw new IllegalArgumentException("Quote amount exceeds booking limit");
        var booking=bookings.saveAndFlush(ParcelBooking.create(owner.merchant()?null:owner.id(),owner.merchant()?owner.id():null,key,hash,request,quote));
        execution.initialize(booking.getId(),null,actor);
        return BookingReadService.snapshot(booking);
    }
    @Transactional(readOnly=true)
    public org.springframework.data.domain.Page<BookingSnapshot> merchantOrders(MerchantContext merchant, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid order page");
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id"));
        return bookings.findByMerchantId(merchant.merchantId(), pageable).map(BookingReadService::snapshot);
    }
    public List<BookingSnapshot> merchantOrders(MerchantContext merchant) {
        return bookings.findTop100ByMerchantIdOrderByCreatedAtDesc(merchant.merchantId()).stream().map(BookingReadService::snapshot).toList();
    }
    @Transactional(readOnly=true)
    public BookingSnapshot merchantOrder(MerchantContext merchant,UUID id) {
        var booking=bookings.findById(id).orElseThrow(()->new AccessDeniedException("Access denied"));
        if(!merchant.merchantId().equals(booking.getMerchantId())) throw new AccessDeniedException("Access denied");
        return BookingReadService.snapshot(booking);
    }
    private String hash(BookingCreateRequest request) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception error) { throw new IllegalStateException("Unable to fingerprint booking request",error); }
    }
}
