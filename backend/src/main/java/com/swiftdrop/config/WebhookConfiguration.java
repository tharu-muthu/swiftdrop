package com.swiftdrop.config;

import com.swiftdrop.integration.webhook.JdkWebhookTransport;
import com.swiftdrop.service.webhook.WebhookDeliveryService;
import com.swiftdrop.service.webhook.WebhookEndpointPolicy;
import com.swiftdrop.service.webhook.WebhookSecretProtector;
import com.swiftdrop.service.webhook.WebhookSigningService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class WebhookConfiguration {
    @Bean
    WebhookSigningService webhookSigningService() { return new WebhookSigningService(); }

    @Bean
    WebhookSecretProtector webhookSecretProtector(AuthSettings settings,
            @Value("${WEBHOOK_SECRET_ENCRYPTION_KEY:}") String configuredKey) {
        try {
            if (configuredKey != null && !configuredKey.isBlank()) {
                return new WebhookSecretProtector(configuredKey);
            }
            byte[] root = Base64.getDecoder().decode(settings.jwtSecret());
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update("swiftdrop/webhook-secrets/v1".getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(root);
            return new WebhookSecretProtector(Base64.getEncoder().encodeToString(digest.digest()));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Webhook secret protection could not be configured.");
        }
    }

    @Bean
    WebhookDeliveryService webhookDeliveryService(AuthSettings settings,
            WebhookSigningService signing, Clock clock) {
        return new WebhookDeliveryService(webhookEndpointPolicy(settings), signing,
                new JdkWebhookTransport(Duration.ofSeconds(5)), clock);
    }

    @Bean
    WebhookEndpointPolicy webhookEndpointPolicy(AuthSettings settings) {
        return new WebhookEndpointPolicy(settings.localhostDevelopment());
    }
}
