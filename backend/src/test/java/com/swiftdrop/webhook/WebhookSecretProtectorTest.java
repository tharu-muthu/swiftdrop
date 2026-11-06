package com.swiftdrop.webhook;

import com.swiftdrop.service.webhook.WebhookSecretProtector;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSecretProtectorTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsAndDecryptsWithoutStoringPlaintext() {
        var protector = new WebhookSecretProtector(KEY);
        var first = protector.encrypt("secret-value");
        var second = protector.encrypt("secret-value");

        assertThat(protector.decrypt(first)).isEqualTo("secret-value");
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(first.nonce()).isNotEqualTo(second.nonce());
    }

    @Test
    void rejectsTamperingAndInvalidKeys() {
        var protector = new WebhookSecretProtector(KEY);
        var encrypted = protector.encrypt("secret-value");
        byte[] tampered = encrypted.ciphertext();
        tampered[0] ^= 1;
        var invalid = new WebhookSecretProtector.ProtectedSecret(tampered, encrypted.nonce());

        assertThatThrownBy(() -> protector.decrypt(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookSecretProtector("c2hvcnQ="))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
