package com.swiftdrop.integration.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.dto.execution.ParcelEvent;
import com.swiftdrop.service.execution.IntegrationReceiptService;
import com.swiftdrop.service.tracking.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="swiftdrop.events",name="enabled",havingValue="true",matchIfMissing=true)
public class TrackingEventConsumer {
    private final ObjectMapper json;
    private final IntegrationReceiptService receipts;
    private final TrackingService tracking;
    private final TrackingSseService sse;
    public TrackingEventConsumer(ObjectMapper json,IntegrationReceiptService receipts,TrackingService tracking,TrackingSseService sse) {
        this.json=json; this.receipts=receipts; this.tracking=tracking; this.sse=sse;
    }
    @RabbitListener(queues="swiftdrop.tracking")
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public void receive(org.springframework.amqp.core.Message message) throws Exception {
        var event=json.readValue(message.getBody(),ParcelEvent.class);
        if(!receipts.claim("tracking",event.eventId())) return;
        // Re-read canonical state so duplicates/out-of-order messages cannot regress the browser.
        var update=tracking.publicTracking(event.trackingNumber());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { update.ifPresent(value->sse.publish(event.trackingNumber(),value)); }
        });
    }
}


