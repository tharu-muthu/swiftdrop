package com.swiftdrop.service.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 signing for outbound webhook bodies. */
public final class WebhookSigningService {
    private static final String ALGORITHM = "HmacSHA256";
    private static final String VERSION = "v1=";
    private final SecureRandom random;

    public WebhookSigningService() {
        this(new SecureRandom());
    }

    WebhookSigningService(SecureRandom random) {
        this.random = random;
    }

    public String generateSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public String sign(String base64Secret, String timestamp, byte[] body) {
        byte[] secret = decodeSecret(base64Secret);
        if (timestamp == null || !timestamp.matches("[0-9]{1,20}")) {
            throw new IllegalArgumentException("Webhook timestamp is invalid.");
        }
        if (body == null) {
            throw new IllegalArgumentException("Webhook body is required.");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return VERSION + Base64.getEncoder().encodeToString(mac.doFinal(body));
        } catch (Exception exception) {
            throw new IllegalStateException("Webhook signature could not be created.", exception);
        }
    }

    public boolean verify(String base64Secret, String timestamp, byte[] body, String signature) {
        if (signature == null) return false;
        try {
            String expected = sign(base64Secret, timestamp, body);
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII));
        } catch (RuntimeException invalidInput) {
            return false;
        }
    }

    public boolean verifyFresh(String base64Secret, String timestamp, byte[] body, String signature,
            Instant now, Duration tolerance) {
        if (now == null || tolerance == null || tolerance.isNegative()) return false;
        try {
            long seconds = Long.parseLong(timestamp);
            long difference = Math.abs(now.getEpochSecond() - seconds);
            return difference <= tolerance.getSeconds() && verify(base64Secret, timestamp, body, signature);
        } catch (RuntimeException invalidTimestamp) {
            return false;
        }
    }

    private byte[] decodeSecret(String base64Secret) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Secret);
            if (decoded.length < 32) throw new IllegalArgumentException();
            return decoded;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Webhook signing secret is invalid.");
        }
    }
}
