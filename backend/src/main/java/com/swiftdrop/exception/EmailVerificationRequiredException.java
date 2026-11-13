package com.swiftdrop.exception;

/** Raised only after a password has been validated for an active account. */
public class EmailVerificationRequiredException extends RuntimeException {
    public EmailVerificationRequiredException() { super("Email verification required."); }
}
