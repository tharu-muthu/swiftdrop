package com.swiftdrop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("swiftdrop.auth")
public record AuthSettings(String issuer, String audience, String jwtSecret,
                           Duration accessTtl, Duration refreshTtl,
                           boolean localhostDevelopment, List<String> allowedOrigins) {
    public AuthSettings {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                || accessTtl == null || accessTtl.toSeconds() < 1 || accessTtl.compareTo(Duration.ofHours(1)) > 0
                || refreshTtl == null || refreshTtl.compareTo(accessTtl) < 0 || refreshTtl.compareTo(Duration.ofDays(30)) > 0
                || allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("Configure auth issuer, audience, lifetimes and allowed origins.");
        }
        allowedOrigins = List.copyOf(allowedOrigins);
        for (String origin : allowedOrigins) {
            try {
                URI uri = URI.create(origin);
                boolean loopback = List.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
                if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                        || (uri.getPath() != null && !uri.getPath().isEmpty())
                        || !("https".equals(uri.getScheme()) || (localhostDevelopment && loopback && "http".equals(uri.getScheme())))
                        || (localhostDevelopment && !loopback)) throw new IllegalArgumentException();
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Auth origins must be exact HTTPS origins, or explicit loopback development origins.");
            }
        }
    }
    @Override public String toString() { return "AuthSettings[redacted]"; }
}
