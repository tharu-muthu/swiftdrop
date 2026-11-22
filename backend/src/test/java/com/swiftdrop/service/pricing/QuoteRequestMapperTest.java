package com.swiftdrop.service.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swiftdrop.dto.booking.AddressContactRequest;
import com.swiftdrop.dto.booking.ParcelDetailsRequest;
import com.swiftdrop.dto.booking.QuotePreparationRequest;
import com.swiftdrop.entity.pricing.ParcelSize;
import org.junit.jupiter.api.Test;

class QuoteRequestMapperTest {
    private final AddressContactRequest pickup =
            new AddressContactRequest("Sender", "0771234567", "1 Main Road", "Colombo", "00100");
    private final AddressContactRequest recipient =
            new AddressContactRequest("Receiver", "0777654321", "2 Beach Road", "Galle", "80000");

    @Test
    void mapsPostalCodesWeightAndSize() {
        var request = new QuotePreparationRequest(pickup, recipient, new ParcelDetailsRequest(1500, "MEDIUM", "Books"));
        var quote = QuoteRequestMapper.from(request);
        assertEquals("00100", quote.pickupPostalCode());
        assertEquals("80000", quote.dropoffPostalCode());
        assertEquals(1500, quote.weightGrams());
        assertEquals(ParcelSize.MEDIUM, quote.size());
    }

    @Test
    void everyFormSizeIsSupported() {
        for (String size : new String[] {"SMALL", "MEDIUM", "LARGE", "EXTRA_LARGE"}) {
            var request = new QuotePreparationRequest(pickup, recipient, new ParcelDetailsRequest(500, size, "Item"));
            assertEquals(ParcelSize.valueOf(size), QuoteRequestMapper.from(request).size());
        }
    }
}