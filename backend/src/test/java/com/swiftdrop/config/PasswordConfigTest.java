package com.swiftdrop.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordConfigTest {
    @Test void existingSha256PasswordHashesRemainUsable() {
        // Fixed synthetic fixture in the original 16-byte salt / 600k / 256-bit hex format.
        String existing = "{pbkdf2-sha256-600k}000102030405060708090a0b0c0d0e0f943ae7bad33f1e5f2ea9871fbe936b87130baafcdf4061e43298e8c5bfbecc3c";
        var encoder = new PasswordConfig().passwordEncoder();
        assertThat(encoder.matches("Legacy test password 12", existing)).isTrue();
        assertThat(encoder.matches("Incorrect password", existing)).isFalse();
    }
}
