package com.swiftdrop.service.webhook;

import com.swiftdrop.integration.webhook.WebhookTransport;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Sends one signed delivery. Durable delivery state is owned by the M9 repository adapter. */
public final class WebhookDeliveryService {
    private final WebhookEndpointPolicy endpoints;
    private final WebhookSigningService signing;
    private final WebhookTransport transport;
    private final Clock clock;

    public WebhookDeliveryService(WebhookEndpointPolicy endpoints, WebhookSigningService signing,
            WebhookTransport transport, Clock clock) {
        this.endpoints = endpoints;
        this.signing = signing;
        this.transport = transport;
        this.clock = clock;
    }

    public DeliveryResult deliver(UUID eventId, String endpoint, String secret, byte[] body) {
        URI destination = endpoints.validate(endpoint);
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        String signature = signing.sign(secret, timestamp, body);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Swiftdrop-Event-Id", eventId.toString());
        headers.put("X-Swiftdrop-Timestamp", timestamp);
        headers.put("X-Swiftdrop-Signature", signature);
        try {
            WebhookTransport.Response response = transport.post(destination, headers, body);
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            return new DeliveryResult(success, response.statusCode(), response.body().length,
                    success ? "DELIVERED" : "FAILED", null);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            return new DeliveryResult(false, 0, 0, "RETRYABLE_FAILURE", exception.getClass().getSimpleName());
        }
    }

    public record DeliveryResult(boolean successful, int statusCode, int responseBytes,
            String state, String failureType) { }
}
