package com.swiftdrop.entity.merchant;

import com.swiftdrop.entity.identity.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "api_credentials", schema = "swiftdrop")
public class ApiCredential extends UuidEntity {
    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "key_prefix", nullable = false, updatable = false, length = 16)
    private String keyPrefix;

    @Column(name = "secret_hash", nullable = false, updatable = false, length = 64)
    private String secretHash;

    @Column(name = "scopes", nullable = false, length = 200)
    private String scopes;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiCredential() { }

    public static ApiCredential issue(UUID merchantId, String keyPrefix, String secretHash, String scopes) {
        var credential = new ApiCredential();
        credential.merchantId = merchantId;
        credential.keyPrefix = keyPrefix;
        credential.secretHash = secretHash;
        credential.scopes = scopes;
        return credential;
    }

    public boolean isRevoked() { return revokedAt != null; }

    public void revoke(Instant when) {
        if (revokedAt == null) {
            revokedAt = when;
        }
    }

    public void markUsed(Instant when) { this.lastUsedAt = when; }

    public Set<String> scopeSet() {
        return Arrays.stream(scopes.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public UUID getMerchantId() { return merchantId; }
    public String getKeyPrefix() { return keyPrefix; }
    public String getSecretHash() { return secretHash; }
    public String getScopes() { return scopes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getRevokedAt() { return revokedAt; }
}