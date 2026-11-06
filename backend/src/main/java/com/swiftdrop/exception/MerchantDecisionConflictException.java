package com.swiftdrop.exception;

public class MerchantDecisionConflictException extends RuntimeException {
    public MerchantDecisionConflictException() { super("This application has already been decided"); }
}