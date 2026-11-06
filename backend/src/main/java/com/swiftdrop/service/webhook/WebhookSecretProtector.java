package com.swiftdrop.service.webhook;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Encrypts signing secrets at rest. The plaintext is only available to the delivery service. */
public final class WebhookSecretProtector {
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random;

    public WebhookSecretProtector(String base64Key) {
        this(base64Key, new SecureRandom());
    }

    WebhookSecretProtector(String base64Key, SecureRandom random) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Key);
            if (decoded.length != 32) throw new IllegalArgumentException();
            this.key = new SecretKeySpec(decoded, "AES");
            this.random = random;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Webhook encryption key must be Base64-encoded 32-byte key.");
        }
    }

    public ProtectedSecret encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("Webhook secret is required.");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new ProtectedSecret(cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8)), nonce);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Webhook secret could not be encrypted.", exception);
        }
    }

    public String decrypt(ProtectedSecret protectedSecret) {
        if (protectedSecret == null || protectedSecret.ciphertext().length == 0
                || protectedSecret.nonce().length != NONCE_BYTES) {
            throw new IllegalArgumentException("Protected webhook secret is invalid.");
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, protectedSecret.nonce()));
            return new String(cipher.doFinal(protectedSecret.ciphertext()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Protected webhook secret is invalid.");
        }
    }

    public record ProtectedSecret(byte[] ciphertext, byte[] nonce) {
        public ProtectedSecret {
            ciphertext = ciphertext.clone();
            nonce = nonce.clone();
        }

        @Override public byte[] ciphertext() { return ciphertext.clone(); }
        @Override public byte[] nonce() { return nonce.clone(); }
    }
}
