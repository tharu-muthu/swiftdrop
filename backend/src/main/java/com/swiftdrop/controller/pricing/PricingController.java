package com.swiftdrop.controller.pricing;

import com.swiftdrop.dto.pricing.PostcodeRequest;
import com.swiftdrop.dto.pricing.QuoteRequest;
import com.swiftdrop.dto.pricing.QuoteResponse;
import com.swiftdrop.dto.pricing.RateCardRequest;
import com.swiftdrop.dto.pricing.RateCardResponse;
import com.swiftdrop.dto.pricing.ZoneRequest;
import com.swiftdrop.dto.pricing.ZoneResponse;
import com.swiftdrop.service.identity.IdentityAccountService;
import com.swiftdrop.service.pricing.PricingAdminService;
import com.swiftdrop.service.pricing.PricingService;
import com.swiftdrop.service.pricing.QuoteOwner;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PricingController {
    private final PricingService pricing;
    private final PricingAdminService admin;
    private final IdentityAccountService identities;

    public PricingController(PricingService pricing, PricingAdminService admin, IdentityAccountService identities) {
        this.pricing = pricing;
        this.admin = admin;
        this.identities = identities;
    }

    @PostMapping("/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    public QuoteResponse createQuote(@Valid @RequestBody QuoteRequest request) {
        return pricing.createQuote(currentCustomer(), request);
    }

    @GetMapping("/quotes/{quoteId}")
    public QuoteResponse getQuote(@PathVariable UUID quoteId) {
        return pricing.getQuote(quoteId, currentCustomer());
    }

    @GetMapping("/admin/zones")
    public List<ZoneResponse> zones() { return admin.listZones(); }

    @PostMapping("/admin/zones")
    @ResponseStatus(HttpStatus.CREATED)
    public ZoneResponse createZone(@Valid @RequestBody ZoneRequest request) { return admin.createZone(request); }

    @PostMapping("/admin/zones/{zoneId}/postcodes")
    @ResponseStatus(HttpStatus.CREATED)
    public void addPostcode(@PathVariable UUID zoneId, @Valid @RequestBody PostcodeRequest request) {
        admin.addPostcode(zoneId, request);
    }

    @GetMapping("/admin/rate-cards")
    public List<RateCardResponse> rateCards() { return admin.listRateCards(); }

    @PostMapping("/admin/rate-cards")
    @ResponseStatus(HttpStatus.CREATED)
    public RateCardResponse createRateCard(@Valid @RequestBody RateCardRequest request) {
        return admin.createRateCard(request);
    }

    private QuoteOwner currentCustomer() {
        return QuoteOwner.customer(identities.requireCurrentIdentity().userId());
    }
}