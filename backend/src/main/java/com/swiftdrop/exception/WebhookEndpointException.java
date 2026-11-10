package com.swiftdrop.exception;

public class WebhookEndpointException extends RuntimeException {
    public WebhookEndpointException() { super("Webhook endpoint is invalid."); }
}
