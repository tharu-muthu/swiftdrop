package com.swiftdrop.exception;

public class MerchantNotApprovedException extends RuntimeException {
    public MerchantNotApprovedException() { super("Merchant account is not approved"); }
}