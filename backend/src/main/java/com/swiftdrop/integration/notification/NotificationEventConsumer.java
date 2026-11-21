package com.swiftdrop.integration.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.dto.execution.ParcelEvent;
import com.swiftdrop.service.notification.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="swiftdrop.events",name="enabled",havingValue="true",matchIfMissing=true)
public class NotificationEventConsumer {
    private final ObjectMapper json; private final NotificationService notifications;
    public NotificationEventConsumer(ObjectMapper json,NotificationService notifications) { this.json=json; this.notifications=notifications; }
    @RabbitListener(queues="swiftdrop.notification")
    public void receive(org.springframework.amqp.core.Message message) throws Exception { notifications.enqueue(json.readValue(message.getBody(),ParcelEvent.class)); }
}

