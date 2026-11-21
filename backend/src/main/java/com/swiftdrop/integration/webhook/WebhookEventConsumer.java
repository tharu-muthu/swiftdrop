package com.swiftdrop.integration.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.dto.execution.ParcelEvent;
import com.swiftdrop.service.webhook.WebhookEventService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="swiftdrop.events",name="enabled",havingValue="true",matchIfMissing=true)
public class WebhookEventConsumer {
    private final ObjectMapper json; private final WebhookEventService events;
    public WebhookEventConsumer(ObjectMapper json,WebhookEventService events) { this.json=json; this.events=events; }
    @RabbitListener(queues="swiftdrop.webhook")
    public void receive(org.springframework.amqp.core.Message message) throws Exception { events.enqueue(json.readValue(message.getBody(),ParcelEvent.class)); }
}

