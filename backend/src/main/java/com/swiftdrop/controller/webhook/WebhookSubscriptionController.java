package com.swiftdrop.controller.webhook;

import com.swiftdrop.dto.webhook.*;
import com.swiftdrop.service.webhook.WebhookSubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookSubscriptionController {
    private final WebhookSubscriptionService webhooks;

    public WebhookSubscriptionController(WebhookSubscriptionService webhooks) { this.webhooks = webhooks; }

    @GetMapping("/api/v1/merchant/webhooks")
    public ResponseEntity<List<WebhookSubscriptionResponse>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(webhooks.list());
    }

    @PostMapping("/api/v1/merchant/webhooks")
    public ResponseEntity<WebhookSubscriptionSecretResponse> create(
            @Valid @RequestBody WebhookSubscriptionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(webhooks.create(request));
    }

    @PatchMapping("/api/v1/merchant/webhooks/{id}")
    public ResponseEntity<WebhookSubscriptionResponse> update(@PathVariable UUID id,
            @Valid @RequestBody WebhookSubscriptionUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(webhooks.update(id, request));
    }

    @DeleteMapping("/api/v1/merchant/webhooks/{id}")
    public ResponseEntity<WebhookSubscriptionResponse> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(webhooks.deactivate(id));
    }

    @PostMapping("/api/v1/merchant/webhooks/{id}/rotate-secret")
    public ResponseEntity<WebhookSubscriptionSecretResponse> rotateSecret(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(webhooks.rotateSecret(id));
    }

    @GetMapping("/api/v1/merchant/webhooks/{id}/deliveries")
    public ResponseEntity<List<WebhookDeliveryResponse>> deliveries(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(webhooks.deliveries(id));
    }

    @PostMapping("/api/v1/merchant/webhooks/{id}/test")
    public ResponseEntity<WebhookTestDeliveryResponse> test(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(webhooks.sendTest(id));
    }
}
