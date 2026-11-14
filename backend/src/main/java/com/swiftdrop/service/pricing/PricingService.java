package com.swiftdrop.service.pricing;

import com.swiftdrop.dto.pricing.PriceBreakdown;
import com.swiftdrop.dto.pricing.QuoteRequest;
import com.swiftdrop.dto.pricing.QuoteResponse;
import com.swiftdrop.entity.pricing.Quotation;
import com.swiftdrop.entity.pricing.RateCardVersion;
import com.swiftdrop.entity.pricing.Zone;
import com.swiftdrop.exception.PricingErrorCode;
import com.swiftdrop.exception.PricingException;
import com.swiftdrop.repository.pricing.QuotationRepository;
import com.swiftdrop.repository.pricing.RateCardRepository;
import com.swiftdrop.repository.pricing.ZonePostcodeRepository;
import com.swiftdrop.repository.pricing.ZoneRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Published pricing contract. M4 (customer booking) and M2 (merchant API) call this with a
 * server-derived QuoteOwner; the owner is never taken from a request body.
 */
@Service
public class PricingService {
    static final Duration QUOTE_TTL = Duration.ofMinutes(10);
    static final int MAX_WEIGHT_GRAMS = 100_000; // demo assumption; confirm with the team

    private final ZonePostcodeRepository postcodes;
    private final ZoneRepository zones;
    private final RateCardRepository rateCards;
    private final QuotationRepository quotations;
    private final PricingStrategy strategy;
    private final Clock clock;

    public PricingService(ZonePostcodeRepository postcodes, ZoneRepository zones, RateCardRepository rateCards,
            QuotationRepository quotations, PricingStrategy strategy, Clock clock) {
        this.postcodes = postcodes;
        this.zones = zones;
        this.rateCards = rateCards;
        this.quotations = quotations;
        this.strategy = strategy;
        this.clock = clock;
    }

    @Transactional
    public QuoteResponse createQuote(QuoteOwner owner, QuoteRequest request) {
        if (request.weightGrams() > MAX_WEIGHT_GRAMS) {
            throw new PricingException(PricingErrorCode.WEIGHT_NOT_SUPPORTED);
        }
        UUID pickupZone = zoneOf(request.pickupPostalCode());
        UUID dropoffZone = zoneOf(request.dropoffPostalCode());
        Instant now = clock.instant();

        RateCardVersion rate = rateCards.findCurrent(pickupZone, dropoffZone, now)
                .orElseThrow(() -> new PricingException(PricingErrorCode.UNSUPPORTED_ROUTE));
        PriceBreakdown price = strategy.calculate(rate, request.weightGrams(), request.size());

        Quotation quote = quotations.save(new Quotation(owner.id(), owner.merchant(), rate.getId(),
                inputHash(owner, pickupZone, dropoffZone, request), request.weightGrams(), request.size(),
                price.currency(), price.baseMinor(), price.extraWeightMinor(), price.sizeSurchargeMinor(),
                price.totalMinor(), now.plus(QUOTE_TTL)));
        return toResponse(quote);
    }

    @Transactional(readOnly = true)
    public QuoteResponse getQuote(UUID quoteId, QuoteOwner owner) {
        return toResponse(ownedQuote(quoteId, owner));
    }

    /**
     * For M4's booking transaction. Wrong owner is reported as not found; an expired quote or changed
     * price-driving inputs return a conflict so the UI can offer recalculation.
     */
    @Transactional(readOnly = true)
    public QuoteResponse validateForBooking(UUID quoteId, QuoteOwner owner, QuoteRequest request) {
        Quotation quote = ownedQuote(quoteId, owner);
        if (quote.isExpiredAt(clock.instant())) {
            throw new PricingException(PricingErrorCode.QUOTE_EXPIRED);
        }
        String hash = inputHash(owner, zoneOf(request.pickupPostalCode()), zoneOf(request.dropoffPostalCode()), request);
        if (!hash.equals(quote.getInputHash())) {
            throw new PricingException(PricingErrorCode.QUOTE_INPUTS_CHANGED);
        }
        return toResponse(quote);
    }

    private Quotation ownedQuote(UUID quoteId, QuoteOwner owner) {
        return quotations.findById(quoteId)
                .filter(quote -> quote.isOwnedBy(owner.id(), owner.merchant()))
                .orElseThrow(() -> new PricingException(PricingErrorCode.QUOTE_NOT_FOUND));
    }

    private UUID zoneOf(String postalCode) {
        var mapping = postcodes.findByPostcode(postalCode.trim())
                .orElseThrow(() -> new PricingException(PricingErrorCode.UNSUPPORTED_POSTCODE));
        zones.findById(mapping.getZoneId())
                .filter(Zone::isActive)
                .orElseThrow(() -> new PricingException(PricingErrorCode.UNSUPPORTED_POSTCODE));
        return mapping.getZoneId();
    }

    private static QuoteResponse toResponse(Quotation q) {
        return new QuoteResponse(q.getId(), q.getAmountMinor(), q.getCurrency(), q.getBaseMinor(),
                q.getExtraWeightMinor(), q.getSizeSurchargeMinor(), q.getExpiresAt());
    }

    private static String inputHash(QuoteOwner owner, UUID pickupZone, UUID dropoffZone, QuoteRequest request) {
        String raw = owner.merchant() + "|" + owner.id() + "|" + pickupZone + "|" + dropoffZone + "|"
                + request.weightGrams() + "|" + request.size();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}