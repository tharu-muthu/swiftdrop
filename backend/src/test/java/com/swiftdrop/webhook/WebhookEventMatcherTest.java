package com.swiftdrop.webhook;

import com.swiftdrop.dto.webhook.WebhookEvent;
import com.swiftdrop.service.webhook.WebhookEventMatcher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookEventMatcherTest {
    @Test
    void matchesOnlyExplicitlySubscribedEventTypes() {
        var event = new WebhookEvent(UUID.randomUUID(), "parcel.status.changed", 1,
                Instant.parse("2026-11-06T10:00:00Z"), UUID.randomUUID(), UUID.randomUUID(),
                "SDP-ABC123", "IN_TRANSIT", 3);
        var matcher = new WebhookEventMatcher();

        assertThat(matcher.matches(List.of("parcel.created", "parcel.status.changed"), event)).isTrue();
        assertThat(matcher.matches(List.of("parcel.created"), event)).isFalse();
        assertThat(matcher.matches(null, event)).isFalse();
    }
}
