package com.swiftdrop.exception;

public class WebhookSubscriptionConflictException extends RuntimeException {
    public WebhookSubscriptionConflictException() { super("A webhook subscription already uses this endpoint."); }
}
