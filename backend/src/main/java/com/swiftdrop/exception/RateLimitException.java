package com.swiftdrop.exception;

public class RateLimitException extends RuntimeException {
    private final long retryAfter;
    public RateLimitException(long retryAfter) { super("Too many attempts."); this.retryAfter = retryAfter; }
    public long retryAfter() { return retryAfter; }
}
