package com.swiftdrop.exception;

public class ApiKeyLimitException extends RuntimeException {
    public ApiKeyLimitException() { super("Active API key limit reached"); }
}