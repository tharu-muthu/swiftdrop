package com.swiftdrop.repository.webhook;

import com.swiftdrop.entity.webhook.WebhookSubscription;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {
    List<WebhookSubscription> findByMerchantIdAndActiveTrue(UUID merchantId);
}
