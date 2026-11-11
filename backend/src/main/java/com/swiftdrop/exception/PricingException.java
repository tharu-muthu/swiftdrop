package com.swiftdrop.exception;

public class PricingException extends RuntimeException {
    private final PricingErrorCode code;

    public PricingException(PricingErrorCode code) {
        super(code.message());
        this.code = code;
    }

    public PricingErrorCode code() { return code; }
}