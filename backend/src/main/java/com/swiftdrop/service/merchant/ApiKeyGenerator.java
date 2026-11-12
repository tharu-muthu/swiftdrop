package com.swiftdrop.service.merchant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyGenerator {
    public record GeneratedKey(String prefix, String key, String hash) { }

    private final SecureRandom random = new SecureRandom();

    public GeneratedKey generate() {
        var prefixBytes = new byte[5];
        random.nextBytes(prefixBytes);
        var prefix = "sdk_" + HexFormat.of().formatHex(prefixBytes);

        var secretBytes = new byte[32];
        random.nextBytes(secretBytes);
        var secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        var key = prefix + "." + secret;
        return new GeneratedKey(prefix, key, sha256Hex(key));
    }

    public static String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}