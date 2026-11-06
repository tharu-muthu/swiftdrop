package com.swiftdrop.webhook;

import com.swiftdrop.service.webhook.WebhookEndpointPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookEndpointPolicyTest {
    @Test
    void acceptsHttpsAndConfiguredLoopbackTestReceiver() {
        var production = new WebhookEndpointPolicy(false);
        var development = new WebhookEndpointPolicy(true);

        assertThat(production.validate("https://merchant.example.test/hooks/status").getScheme()).isEqualTo("https");
        assertThat(development.validate("http://127.0.0.1:18080/webhook").getHost()).isEqualTo("127.0.0.1");
    }

    @Test
    void rejectsUnsafeDestinations() {
        var policy = new WebhookEndpointPolicy(false);
        for (String value : new String[] {
                "http://merchant.example.test/hook",
                "https://user:password@merchant.example.test/hook",
                "https://merchant.example.test/hook#fragment",
                "file:///tmp/webhook",
                "https://127.0.0.1/hook",
                "https://merchant.example.test"
        }) {
            assertThatThrownBy(() -> policy.validate(value)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
