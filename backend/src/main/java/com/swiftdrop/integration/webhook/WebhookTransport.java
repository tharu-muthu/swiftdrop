package com.swiftdrop.integration.webhook;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

public interface WebhookTransport {
    Response post(URI endpoint, Map<String, String> headers, byte[] body) throws IOException, InterruptedException;

    record Response(int statusCode, byte[] body) {
        public Response {
            body = body == null ? new byte[0] : body.clone();
        }

        @Override public byte[] body() { return body.clone(); }
    }
}
