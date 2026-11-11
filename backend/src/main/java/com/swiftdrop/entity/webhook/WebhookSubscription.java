package com.swiftdrop.entity.webhook;

import com.swiftdrop.entity.identity.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "webhook_subscriptions", schema = "swiftdrop")
public class WebhookSubscription extends UuidEntity {
    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "callback_url", nullable = false, length = 2048)
    private String callbackUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_types", nullable = false, columnDefinition = "jsonb")
    private List<String> eventTypes;

    @Column(name = "encrypted_signing_secret", nullable = false)
    private byte[] encryptedSigningSecret;

    @Column(name = "signing_secret_nonce", nullable = false)
    private byte[] signingSecretNonce;

    @Column(name = "secret_key_version", nullable = false, length = 32)
    private String secretKeyVersion;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected WebhookSubscription() { }

    public static WebhookSubscription create(UUID merchantId, String callbackUrl, List<String> eventTypes,
            byte[] encryptedSecret, byte[] nonce, String secretKeyVersion) {
        var subscription = new WebhookSubscription();
        subscription.merchantId = merchantId;
        subscription.callbackUrl = callbackUrl;
        subscription.eventTypes = new ArrayList<>(new LinkedHashSet<>(eventTypes));
        subscription.encryptedSigningSecret = encryptedSecret.clone();
        subscription.signingSecretNonce = nonce.clone();
        subscription.secretKeyVersion = secretKeyVersion;
        subscription.active = true;
        return subscription;
    }

    public void updateConfiguration(String callbackUrl, List<String> eventTypes) {
        this.callbackUrl = callbackUrl;
        this.eventTypes = new ArrayList<>(new LinkedHashSet<>(eventTypes));
    }

    public void setActive(boolean active) { this.active = active; }

    public void rotateSecret(byte[] encryptedSecret, byte[] nonce, String secretKeyVersion) {
        this.encryptedSigningSecret = encryptedSecret.clone();
        this.signingSecretNonce = nonce.clone();
        this.secretKeyVersion = secretKeyVersion;
    }

    public UUID getMerchantId() { return merchantId; }
    public String getCallbackUrl() { return callbackUrl; }
    public List<String> getEventTypes() { return eventTypes == null ? List.of() : List.copyOf(eventTypes); }
    public byte[] getEncryptedSigningSecret() { return encryptedSigningSecret.clone(); }
    public byte[] getSigningSecretNonce() { return signingSecretNonce.clone(); }
    public String getSecretKeyVersion() { return secretKeyVersion; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
