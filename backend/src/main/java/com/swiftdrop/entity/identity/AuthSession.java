package com.swiftdrop.entity.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "auth_sessions", schema = "swiftdrop")
public class AuthSession extends CreatedEntity {
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "authorization_version", nullable = false)
    private long authorizationVersion;

    @Version
    @Column(nullable = false)
    private long version;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 100)
    private String revocationReason;

    protected AuthSession() { }

    public UUID getUserId() { return userId; }
    public UUID getFamilyId() { return familyId; }
    public long getAuthorizationVersion() { return authorizationVersion; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevocationReason() { return revocationReason; }
}
