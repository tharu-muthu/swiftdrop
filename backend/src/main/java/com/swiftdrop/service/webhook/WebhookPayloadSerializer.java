package com.swiftdrop.service.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.dto.webhook.WebhookEvent;
import java.nio.charset.StandardCharsets;

/** Serializes the exact bytes used for both the request body and its signature. */
public final class WebhookPayloadSerializer {
    private final ObjectMapper mapper;

    public WebhookPayloadSerializer(ObjectMapper mapper) {
        this.mapper = mapper.copy();
    }

    public byte[] serialize(WebhookEvent event) {
        try {
            return mapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Webhook event could not be serialized.", exception);
        }
    }
}
