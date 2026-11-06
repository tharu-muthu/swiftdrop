package com.swiftdrop.service.webhook;

import com.swiftdrop.dto.webhook.WebhookEvent;
import java.util.Collection;

/** Matches a committed event to a subscription's explicitly selected event types. */
public final class WebhookEventMatcher {
    public boolean matches(Collection<String> subscribedEventTypes, WebhookEvent event) {
        return subscribedEventTypes != null && event != null
                && subscribedEventTypes.stream().anyMatch(event.eventType()::equals);
    }
}
