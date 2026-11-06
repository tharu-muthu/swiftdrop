package com.swiftdrop.exception;

public class MerchantAlreadyRegisteredException extends RuntimeException {
    public MerchantAlreadyRegisteredException() {
        super("A business is already registered for this account");
    }
}