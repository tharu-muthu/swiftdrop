package com.swiftdrop.service.webhook;

import java.net.URI;
import java.util.Locale;

/** Validates webhook destinations before any network operation is attempted. */
public final class WebhookEndpointPolicy {
    private static final int MAX_LENGTH = 2048;
    private final boolean allowLoopbackHttp;

    public WebhookEndpointPolicy(boolean allowLoopbackHttp) {
        this.allowLoopbackHttp = allowLoopbackHttp;
    }

    public URI validate(String rawEndpoint) {
        if (rawEndpoint == null || rawEndpoint.isBlank() || rawEndpoint.length() > MAX_LENGTH
                || !rawEndpoint.equals(rawEndpoint.trim())) {
            throw invalid();
        }
        try {
            URI endpoint = URI.create(rawEndpoint);
            String scheme = endpoint.getScheme() == null ? "" : endpoint.getScheme().toLowerCase(Locale.ROOT);
            String host = endpoint.getHost();
            if (host == null || endpoint.getUserInfo() != null || endpoint.getFragment() != null
                    || endpoint.getPath() == null || endpoint.getPath().isBlank()) {
                throw invalid();
            }
            boolean https = "https".equals(scheme);
            boolean localHttp = "http".equals(scheme) && allowLoopbackHttp && isLoopback(host);
            if (!https && !localHttp) throw invalid();
            if (isLoopback(host) && !localHttp) throw invalid();
            if (endpoint.getPort() == 0 || endpoint.getPort() < -1) throw invalid();
            if (isIpLiteral(host) && !isLoopback(host)) throw invalid();
            return endpoint;
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private boolean isLoopback(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost") || normalized.equals("127.0.0.1")
                || normalized.equals("[::1]") || normalized.equals("::1");
    }

    private boolean isIpLiteral(String host) {
        String normalized = host.replace("[", "").replace("]", "");
        return normalized.matches("[0-9.]+") || normalized.contains(":");
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("Webhook endpoint is not an allowed HTTPS destination.");
    }
}
