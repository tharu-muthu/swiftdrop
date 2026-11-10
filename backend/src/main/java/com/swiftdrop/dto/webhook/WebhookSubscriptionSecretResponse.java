package com.swiftdrop.dto.webhook;

public record WebhookSubscriptionSecretResponse(WebhookSubscriptionResponse subscription,
        String signingSecret) { }
