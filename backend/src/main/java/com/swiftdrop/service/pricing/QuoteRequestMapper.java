package com.swiftdrop.service.pricing;

import com.swiftdrop.dto.booking.QuotePreparationRequest;
import com.swiftdrop.dto.pricing.QuoteRequest;
import com.swiftdrop.entity.pricing.ParcelSize;

/** Turns M4's booking-form request into M3's price-driving inputs. */
public final class QuoteRequestMapper {
    private QuoteRequestMapper() { }

    public static QuoteRequest from(QuotePreparationRequest request) {
        return new QuoteRequest(request.pickup().postalCode(), request.recipient().postalCode(),
                request.parcel().weightGrams(), ParcelSize.valueOf(request.parcel().size()));
    }
}