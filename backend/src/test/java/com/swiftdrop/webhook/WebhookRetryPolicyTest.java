package com.swiftdrop.webhook;

import com.swiftdrop.service.webhook.WebhookRetryPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookRetryPolicyTest {
    private final WebhookRetryPolicy policy = new WebhookRetryPolicy(4, Duration.ofSeconds(2), Duration.ofSeconds(10));

    @Test
    void retriesTransientResponsesWithCappedBackoff() {
        assertThat(policy.response(1, 503)).isEqualTo(new WebhookRetryPolicy.Decision(true, Duration.ofSeconds(2), "RETRYING"));
        assertThat(policy.response(2, 429)).isEqualTo(new WebhookRetryPolicy.Decision(true, Duration.ofSeconds(4), "RETRYING"));
        assertThat(policy.response(3, 500)).isEqualTo(new WebhookRetryPolicy.Decision(true, Duration.ofSeconds(8), "RETRYING"));
        assertThat(policy.response(4, 503).retry()).isFalse();
    }

    @Test
    void doesNotRetryPermanentResponses() {
        assertThat(policy.response(1, 400).retry()).isFalse();
        assertThat(policy.response(1, 404).state()).isEqualTo("FAILED");
        assertThat(policy.networkFailure(1).retry()).isTrue();
    }
}
