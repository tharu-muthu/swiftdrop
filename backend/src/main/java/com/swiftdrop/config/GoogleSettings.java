package com.swiftdrop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("swiftdrop.google")
public record GoogleSettings(boolean enabled, String clientId, String clientSecret, String frontendOrigin) {
    @Override public String toString() { return "GoogleSettings[redacted]"; }
    public GoogleSettings {
        if (frontendOrigin == null || frontendOrigin.isBlank())
            frontendOrigin = "http://127.0.0.1:5173";
        try {
            var uri = java.net.URI.create(frontendOrigin);
            boolean loopback = java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost() == null ? "" : uri.getHost());
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !uri.getPath().isEmpty() || !("https".equals(uri.getScheme()) || (loopback && "http".equals(uri.getScheme()))))
                throw new IllegalArgumentException();
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Configure GOOGLE_FRONTEND_ORIGIN as an exact HTTPS origin or HTTP loopback origin.");
        }
        if (enabled && (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()))
            throw new IllegalArgumentException("Google OAuth requires GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET when enabled.");
    }
}

