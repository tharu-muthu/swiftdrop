package com.swiftdrop.exception;

import org.springframework.security.core.AuthenticationException;

public class InvalidApiKeyException extends AuthenticationException {
    public InvalidApiKeyException() { super("Invalid or revoked API key"); }
}