package com.swiftdrop.integration.webhook;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Small outbound adapter with bounded response reads and redirect rejection. */
public final class JdkWebhookTransport implements WebhookTransport {
    private static final int MAX_RESPONSE_BYTES = 8192;
    private final HttpClient client;

    public JdkWebhookTransport(Duration connectTimeout) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Response post(URI endpoint, Map<String, String> headers, byte[] body)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(request::header);
        HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            return new Response(response.statusCode(), input.readNBytes(MAX_RESPONSE_BYTES));
        }
    }
}
