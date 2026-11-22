package com.swiftdrop.service.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.dto.merchant.MerchantContext;
import com.swiftdrop.dto.webhook.*;
import com.swiftdrop.entity.webhook.WebhookDelivery;
import com.swiftdrop.entity.webhook.WebhookSubscription;
import com.swiftdrop.exception.WebhookEndpointException;
import com.swiftdrop.exception.WebhookSubscriptionConflictException;
import com.swiftdrop.exception.WebhookSubscriptionNotFoundException;
import com.swiftdrop.repository.webhook.WebhookDeliveryAttemptRepository;
import com.swiftdrop.repository.webhook.WebhookDeliveryRepository;
import com.swiftdrop.repository.webhook.WebhookSubscriptionRepository;
import com.swiftdrop.service.merchant.MerchantAccessService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookSubscriptionService {
    private static final String KEY_VERSION = "v1";
    private static final String TEST_EVENT = "webhook.test";

    private final MerchantAccessService merchants;
    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookDeliveryAttemptRepository attempts;
    private final WebhookEndpointPolicy endpoints;
    private final WebhookSigningService signing;
    private final WebhookSecretProtector secrets;
    private final WebhookPayloadSerializer payloads;
    private final Clock clock;

    public WebhookSubscriptionService(MerchantAccessService merchants,
            WebhookSubscriptionRepository subscriptions, WebhookDeliveryRepository deliveries,
            WebhookDeliveryAttemptRepository attempts, WebhookEndpointPolicy endpoints,
            WebhookSigningService signing, WebhookSecretProtector secrets,
            ObjectMapper mapper, Clock clock) {
        this.merchants = merchants;
        this.subscriptions = subscriptions;
        this.deliveries = deliveries;
        this.attempts = attempts;
        this.endpoints = endpoints;
        this.signing = signing;
        this.secrets = secrets;
        this.payloads = new WebhookPayloadSerializer(mapper);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WebhookSubscriptionResponse> list() {
        MerchantContext merchant = merchants.requireApprovedOwnerMerchant();
        return subscriptions.findByMerchantIdOrderByCreatedAtDesc(merchant.merchantId()).stream()
                .map(WebhookSubscriptionResponse::from).toList();
    }

    @Transactional
    public WebhookSubscriptionSecretResponse create(WebhookSubscriptionCreateRequest request) {
        MerchantContext merchant = merchants.requireApprovedOwnerMerchant();
        String callbackUrl = endpoint(request.callbackUrl());
        List<String> eventTypes = eventTypes(request.eventTypes());
        if (subscriptions.existsByMerchantIdAndCallbackUrl(merchant.merchantId(), callbackUrl)) {
            throw new WebhookSubscriptionConflictException();
        }
        String plaintext = signing.generateSecret();
        WebhookSecretProtector.ProtectedSecret protectedSecret = secrets.encrypt(plaintext);
        WebhookSubscription subscription = WebhookSubscription.create(merchant.merchantId(), callbackUrl,
                eventTypes, protectedSecret.ciphertext(), protectedSecret.nonce(), KEY_VERSION);
        try {
            subscriptions.saveAndFlush(subscription);
        } catch (DataIntegrityViolationException duplicate) {
            throw new WebhookSubscriptionConflictException();
        }
        return new WebhookSubscriptionSecretResponse(WebhookSubscriptionResponse.from(subscription), plaintext);
    }

    @Transactional
    public WebhookSubscriptionResponse update(UUID id, WebhookSubscriptionUpdateRequest request) {
        MerchantContext merchant = merchants.requireApprovedOwnerMerchant();
        if (request.callbackUrl() == null && request.eventTypes() == null && request.active() == null) {
            throw new IllegalArgumentException("A webhook update must contain a permitted field.");
        }
        WebhookSubscription subscription = owned(id, merchant.merchantId());
        String callbackUrl = request.callbackUrl() == null ? subscription.getCallbackUrl() : endpoint(request.callbackUrl());
        List<String> eventTypes = request.eventTypes() == null ? subscription.getEventTypes() : eventTypes(request.eventTypes());
        if (!callbackUrl.equals(subscription.getCallbackUrl())
                && subscriptions.existsByMerchantIdAndCallbackUrlAndIdNot(merchant.merchantId(), callbackUrl, id)) {
            throw new WebhookSubscriptionConflictException();
        }
        subscription.updateConfiguration(callbackUrl, eventTypes);
        if (request.active() != null) subscription.setActive(request.active());
        try {
            subscriptions.saveAndFlush(subscription);
        } catch (DataIntegrityViolationException duplicate) {
            throw new WebhookSubscriptionConflictException();
        }
        return WebhookSubscriptionResponse.from(subscription);
    }

    @Transactional
    public WebhookSubscriptionResponse deactivate(UUID id) {
        MerchantContext merchant = merchants.requireApprovedOwnerMerchant();
        WebhookSubscription subscription = owned(id, merchant.merchantId());
        subscription.setActive(false);
        subscriptions.saveAndFlush(subscription);
        return WebhookSubscriptionResponse.from(subscription);
    }

    @Transactional
    public WebhookSubscriptionSecretResponse rotateSecret(UUID id) {
        MerchantContext merchant = merchants.requireApprovedOwnerMerchant();
        WebhookSubscription subscription = owned(id, merchant.merchantId());
        String plaintext = signing.generateSecret();
        WebhookSecretProtector.ProtectedSecret protectedSecret = secrets.encrypt(plaintext);
        subscription.rotateSecret(protectedSecret.ciphertext(), protectedSecret.nonce(), KEY_VERSION);
        subscriptions.saveAndFlush(subscription);
        return new WebhookSubscriptionSecretResponse(WebhookSubscriptionResponse.from(subscription), plaintext);
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryResponse> deliveries(UUID id) {
        MerchantContext merchant = merchants.requireApprovedOwnerMerchant();
        WebhookSubscription subscription = owned(id, merchant.merchantId());
        return deliveries.findBySubscriptionIdOrderByCreatedAtDesc(subscription.getId()).stream()
                .map(WebhookDeliveryResponse::from).toList();
    }

    @Transactional
    public WebhookTestDeliveryResponse sendTest(UUID id) {
        MerchantContext merchant = merchants.requireApprovedOwnerMerchant();
        WebhookSubscription subscription = owned(id, merchant.merchantId());
        if (!subscription.isActive()) throw new IllegalArgumentException("Enable the webhook before testing it.");
        UUID eventId = UUID.randomUUID();
        WebhookEvent event = new WebhookEvent(eventId, TEST_EVENT, 1, clock.instant(), merchant.merchantId(),
                UUID.randomUUID(), "SDP-TEST-" + eventId.toString().substring(0, 8).toUpperCase(), "TEST", 0);
        byte[] body = payloads.serialize(event);
        String payloadBody = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        WebhookDelivery record = WebhookDelivery.create(subscription, eventId, TEST_EVENT, payloadBody);
        deliveries.saveAndFlush(record);
        // The worker claims this committed job. HTTP never runs in the subscription transaction.
        return new WebhookTestDeliveryResponse(WebhookDeliveryResponse.from(record));
    }

    private WebhookSubscription owned(UUID id, UUID merchantId) {
        return subscriptions.findById(id).filter(found -> merchantId.equals(found.getMerchantId()))
                .orElseThrow(WebhookSubscriptionNotFoundException::new);
    }
    @Transactional(readOnly=true)
    public List<WebhookAttemptResponse> attempts(UUID subscriptionId,UUID deliveryId) {
        var merchant=merchants.requireApprovedOwnerMerchant(); owned(subscriptionId,merchant.merchantId());
        var record=deliveries.findById(deliveryId).filter(row->row.getSubscription().getId().equals(subscriptionId)).orElseThrow(WebhookSubscriptionNotFoundException::new);
        return attempts.findByDeliveryIdOrderByAttemptNumberAsc(record.getId()).stream().map(row->new WebhookAttemptResponse(row.getAttemptNumber(),row.getStartedAt(),row.getCompletedAt(),row.getHttpStatus(),row.getOutcome(),row.getErrorMessage())).toList();
    }
    @Transactional public WebhookDeliveryResponse retry(UUID subscriptionId,UUID deliveryId) {
        var merchant=merchants.requireApprovedOwnerMerchant(); var subscription=owned(subscriptionId,merchant.merchantId());
        if(!subscription.isActive()) throw new IllegalArgumentException("Subscription must be active");
        var record=deliveries.findById(deliveryId).filter(row->row.getSubscription().getId().equals(subscriptionId)).orElseThrow(WebhookSubscriptionNotFoundException::new);
        if(!"FAILED".equals(record.getDeliveryState())) throw new IllegalArgumentException("Only failed deliveries may be retried");
        record.retryAt(clock.instant()); deliveries.saveAndFlush(record); return WebhookDeliveryResponse.from(record);
    }

    private String endpoint(String raw) {
        try { return endpoints.validate(raw).toString(); }
        catch (IllegalArgumentException invalid) { throw new WebhookEndpointException(); }
    }

    private List<String> eventTypes(List<String> raw) {
        if (raw == null || raw.isEmpty()) throw new IllegalArgumentException("At least one event type is required.");
        List<String> normalized = raw.stream().map(String::trim).filter(value -> !value.isBlank()).distinct().sorted().toList();
        if (normalized.isEmpty() || normalized.stream().anyMatch(value -> !value.equals("parcel.status.changed"))) {
            throw new IllegalArgumentException("Webhook event types are invalid.");
        }
        return normalized;
    }
}
