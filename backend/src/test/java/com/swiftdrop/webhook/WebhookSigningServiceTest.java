package com.swiftdrop.webhook;

import com.swiftdrop.service.webhook.WebhookSigningService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSigningServiceTest {
    @Test
    void generatedSecretSignsAndVerifiesExactBody() {
        var signing = new WebhookSigningService();
        String secret = signing.generateSecret();
        byte[] body = "{\"status\":\"IN_TRANSIT\"}".getBytes(StandardCharsets.UTF_8);

        String signature = signing.sign(secret, "1730887200", body);

        assertThat(signature).startsWith("v1=");
        assertThat(signing.verify(secret, "1730887200", body, signature)).isTrue();
        assertThat(signing.verifyFresh(secret, "1730887200", body, signature,
                Instant.ofEpochSecond(1730887205), Duration.ofSeconds(10))).isTrue();
        assertThat(signing.verifyFresh(secret, "1730887200", body, signature,
                Instant.ofEpochSecond(1730887300), Duration.ofSeconds(10))).isFalse();
        assertThat(signing.verify(secret, "1730887200", "changed".getBytes(StandardCharsets.UTF_8), signature)).isFalse();
        assertThat(signing.verify(secret, "1730887201", body, signature)).isFalse();
    }

    @Test
    void rejectsShortOrMalformedSecrets() {
        var signing = new WebhookSigningService();
        assertThatThrownBy(() -> signing.sign("c2hvcnQ=", "1", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(signing.verify("invalid", "1", new byte[0], "v1=bad")).isFalse();
    }
}
