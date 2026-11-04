package com.swiftdrop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("swiftdrop.google")
public record GoogleSettings(boolean enabled, String clientId, String clientSecret, String frontendOrigin) {
    public GoogleSettings {
        if (frontendOrigin == null || frontendOrigin.isBlank())
            frontendOrigin = "http://127.0.0.1:5173";
        if (enabled && (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()))
            throw new IllegalArgumentException("Google OAuth requires GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET when enabled.");
    }
}

