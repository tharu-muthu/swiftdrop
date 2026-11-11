package com.swiftdrop.service.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swiftdrop.dto.pricing.QuoteRequest;
import com.swiftdrop.entity.pricing.ParcelSize;
import com.swiftdrop.entity.pricing.Quotation;
import com.swiftdrop.entity.pricing.RateCardVersion;
import com.swiftdrop.entity.pricing.Zone;
import com.swiftdrop.entity.pricing.ZonePostcode;
import com.swiftdrop.exception.PricingErrorCode;
import com.swiftdrop.exception.PricingException;
import com.swiftdrop.repository.pricing.QuotationRepository;
import com.swiftdrop.repository.pricing.RateCardRepository;
import com.swiftdrop.repository.pricing.ZonePostcodeRepository;
import com.swiftdrop.repository.pricing.ZoneRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PricingServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private final ZonePostcodeRepository postcodes = mock(ZonePostcodeRepository.class);
    private final ZoneRepository zones = mock(ZoneRepository.class);
    private final RateCardRepository rateCards = mock(RateCardRepository.class);
    private final QuotationRepository quotations = mock(QuotationRepository.class);
    private final AtomicReference<Quotation> saved = new AtomicReference<>();

    private final UUID zoneA = UUID.randomUUID();
    private final UUID zoneB = UUID.randomUUID();
    private final QuoteOwner alice = QuoteOwner.customer(UUID.randomUUID());
    private final QuoteOwner bob = QuoteOwner.customer(UUID.randomUUID());
    private final QuoteRequest request = new QuoteRequest("00100", "80000", 1_500, ParcelSize.M);

    @BeforeEach
    void stubData() {
        when(postcodes.findByPostcode("00100")).thenReturn(Optional.of(new ZonePostcode("00100", zoneA)));
        when(postcodes.findByPostcode("80000")).thenReturn(Optional.of(new ZonePostcode("80000", zoneB)));
        when(zones.findById(any())).thenReturn(Optional.of(new Zone("Test zone")));
        when(rateCards.findCurrent(any(), any(), any())).thenReturn(Optional.of(new RateCardVersion(zoneA, zoneB,
                45_000, 1, 15_000, 0, 10_000, 25_000, Instant.parse("2026-01-01T00:00:00Z"))));
        when(quotations.save(any(Quotation.class))).thenAnswer(call -> {
            saved.set(call.getArgument(0));
            return saved.get();
        });
        when(quotations.findById(any())).thenAnswer(call -> Optional.ofNullable(saved.get()));
    }

    private PricingService serviceAt(Instant now) {
        return new PricingService(postcodes, zones, rateCards, quotations, new StandardPricingStrategy(),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void createsQuoteWithServerCalculatedAmountAndTenMinuteExpiry() {
        var response = serviceAt(NOW).createQuote(alice, request);
        assertEquals(45_000 + 15_000 + 10_000, response.amountMinor());
        assertEquals(NOW.plusSeconds(600), response.expiresAt());
    }

    @Test
    void unknownPostcodeIsRejected() {
        var bad = new QuoteRequest("99999", "80000", 1_000, ParcelSize.S);
        var error = assertThrows(PricingException.class, () -> serviceAt(NOW).createQuote(alice, bad));
        assertEquals(PricingErrorCode.UNSUPPORTED_POSTCODE, error.code());
    }

    @Test
    void excessiveWeightIsRejected() {
        var heavy = new QuoteRequest("00100", "80000", 30_001, ParcelSize.S);
        var error = assertThrows(PricingException.class, () -> serviceAt(NOW).createQuote(alice, heavy));
        assertEquals(PricingErrorCode.WEIGHT_NOT_SUPPORTED, error.code());
    }

    @Test
    void noRateForRouteIsRejected() {
        when(rateCards.findCurrent(any(), any(), any())).thenReturn(Optional.empty());
        var error = assertThrows(PricingException.class, () -> serviceAt(NOW).createQuote(alice, request));
        assertEquals(PricingErrorCode.UNSUPPORTED_ROUTE, error.code());
    }

    @Test
    void anotherUsersQuoteLooksNotFound() {
        serviceAt(NOW).createQuote(alice, request);
        var error = assertThrows(PricingException.class,
                () -> serviceAt(NOW).getQuote(UUID.randomUUID(), bob));
        assertEquals(PricingErrorCode.QUOTE_NOT_FOUND, error.code());
    }

    @Test
    void expiredQuoteFailsBookingValidation() {
        serviceAt(NOW).createQuote(alice, request);
        var later = serviceAt(NOW.plusSeconds(601));
        var error = assertThrows(PricingException.class,
                () -> later.validateForBooking(UUID.randomUUID(), alice, request));
        assertEquals(PricingErrorCode.QUOTE_EXPIRED, error.code());
    }

    @Test
    void changedWeightFailsBookingValidation() {
        serviceAt(NOW).createQuote(alice, request);
        var changed = new QuoteRequest("00100", "80000", 3_000, ParcelSize.M);
        var error = assertThrows(PricingException.class,
                () -> serviceAt(NOW).validateForBooking(UUID.randomUUID(), alice, changed));
        assertEquals(PricingErrorCode.QUOTE_INPUTS_CHANGED, error.code());
    }

    @Test
    void unchangedInputsPassBookingValidation() {
        serviceAt(NOW).createQuote(alice, request);
        var response = serviceAt(NOW).validateForBooking(UUID.randomUUID(), alice, request);
        assertEquals(70_000, response.amountMinor());
    }
}