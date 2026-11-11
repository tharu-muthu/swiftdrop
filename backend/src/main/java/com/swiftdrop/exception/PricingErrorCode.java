package com.swiftdrop.exception;

public enum PricingErrorCode {
    QUOTE_NOT_FOUND(404, "Quote not found."),
    QUOTE_EXPIRED(409, "This quote has expired. Please request a new quote."),
    QUOTE_INPUTS_CHANGED(409, "The delivery details changed. Please request a new quote."),
    UNSUPPORTED_POSTCODE(422, "We do not deliver to or from one of these postal codes yet."),
    UNSUPPORTED_ROUTE(422, "There is no delivery rate for this route yet."),
    WEIGHT_NOT_SUPPORTED(422, "This parcel weight is not supported."),
    ZONE_NOT_FOUND(404, "Zone not found."),
    ZONE_NAME_TAKEN(409, "A zone with this name already exists."),
    POSTCODE_ALREADY_MAPPED(409, "This postal code is already mapped to a zone."),
    RATE_CARD_EXISTS(409, "A rate card for this route and start time already exists.");

    private final int status;
    private final String message;

    PricingErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int status() { return status; }
    public String message() { return message; }
}