package com.swiftdrop.exception;

public class MerchantNotFoundException extends RuntimeException {
    public MerchantNotFoundException() { super("Merchant application not found"); }
}