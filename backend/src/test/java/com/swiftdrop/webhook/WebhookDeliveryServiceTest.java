package com.swiftdrop.webhook;

import com.swiftdrop.integration.webhook.WebhookTransport;
import com.swiftdrop.service.webhook.WebhookDeliveryService;
import com.swiftdrop.service.webhook.WebhookEndpointPolicy;
import com.swiftdrop.service.webhook.WebhookSigningService;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookDeliveryServiceTest {
    @Test
    void sendsSignedBodyAndRequiredHeaders() {
        var signing = new WebhookSigningService();
        String secret = signing.generateSecret();
        var transport = new CapturingTransport(new WebhookTransport.Response(204, new byte[0]));
        var service = new WebhookDeliveryService(new WebhookEndpointPolicy(true), signing, transport,
                Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC));
        UUID eventId = UUID.fromString("8c1a8d08-18d0-4b28-a0d6-516a03d52ca9");
        byte[] body = "{\"eventId\":\"8c1a8d08-18d0-4b28-a0d6-516a03d52ca9\"}".getBytes();

        var result = service.deliver(eventId, "http://127.0.0.1:18080/webhook", secret, body);

        assertThat(result.successful()).isTrue();
        assertThat(transport.endpoint).isEqualTo(URI.create("http://127.0.0.1:18080/webhook"));
        assertThat(transport.headers).containsEntry("X-Swiftdrop-Event-Id", eventId.toString());
        assertThat(transport.headers).containsEntry("X-Swiftdrop-Timestamp", "1793959200");
        assertThat(signing.verify(secret, transport.headers.get("X-Swiftdrop-Timestamp"), transport.body,
                transport.headers.get("X-Swiftdrop-Signature"))).isTrue();
    }

    private static final class CapturingTransport implements WebhookTransport {
        private final Response response;
        private URI endpoint;
        private Map<String, String> headers;
        private byte[] body;

        private CapturingTransport(Response response) { this.response = response; }

        @Override
        public Response post(URI endpoint, Map<String, String> headers, byte[] body) {
            this.endpoint = endpoint;
            this.headers = headers;
            this.body = body;
            return response;
        }
    }
}
