package com.swiftdrop.dto.webhook;

import com.swiftdrop.entity.webhook.WebhookSubscription;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookSubscriptionResponse(UUID id, String callbackUrl, List<String> eventTypes,
        boolean active, Instant createdAt, Instant updatedAt, long version) {
    public static WebhookSubscriptionResponse from(WebhookSubscription subscription) {
        return new WebhookSubscriptionResponse(subscription.getId(), subscription.getCallbackUrl(),
                subscription.getEventTypes(), subscription.isActive(), subscription.getCreatedAt(),
                subscription.getUpdatedAt(), subscription.getVersion());
    }
}
