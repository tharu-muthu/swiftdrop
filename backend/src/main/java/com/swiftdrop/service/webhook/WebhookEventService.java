package com.swiftdrop.service.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.dto.execution.ParcelEvent;
import com.swiftdrop.dto.webhook.WebhookEvent;
import com.swiftdrop.service.execution.IntegrationReceiptService;
import com.swiftdrop.repository.webhook.WebhookJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookEventService {
    private final WebhookJobRepository jobs; private final IntegrationReceiptService receipts; private final ObjectMapper json;
    public WebhookEventService(WebhookJobRepository jobs,IntegrationReceiptService receipts,ObjectMapper json) { this.jobs=jobs; this.receipts=receipts; this.json=json; }
    @Transactional(rollbackFor=Exception.class)
    public void enqueue(ParcelEvent event) throws Exception {
        if(!receipts.claim("webhook",event.eventId())||event.merchantId()==null) return;
        var payload=new WebhookEvent(event.eventId(),event.eventType(),event.schemaVersion(),event.occurredAt(),
                event.merchantId(),event.parcelId(),event.trackingNumber(),event.status(),event.sequence());
        jobs.enqueue(event.merchantId(),event.eventId(),event.eventType(),json.writeValueAsString(payload));
    }
}

