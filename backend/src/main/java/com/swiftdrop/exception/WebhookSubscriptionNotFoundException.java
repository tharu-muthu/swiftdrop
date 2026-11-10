package com.swiftdrop.exception;

public class WebhookSubscriptionNotFoundException extends RuntimeException {
    public WebhookSubscriptionNotFoundException() { super("Webhook subscription not found."); }
}
