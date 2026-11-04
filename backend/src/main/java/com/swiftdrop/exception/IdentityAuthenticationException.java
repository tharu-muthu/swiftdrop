package com.swiftdrop.exception;

public class IdentityAuthenticationException extends RuntimeException {
    public IdentityAuthenticationException() { super("Invalid identity credentials."); }
}
