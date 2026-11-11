package com.swiftdrop.repository.webhook;

import com.swiftdrop.entity.webhook.WebhookDelivery;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    Optional<WebhookDelivery> findBySubscriptionIdAndEventId(UUID subscriptionId, UUID eventId);
    java.util.List<WebhookDelivery> findBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId);
    java.util.List<WebhookDelivery> findTop100ByDeliveryStateAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            String deliveryState, Instant now);
}
