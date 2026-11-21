package com.swiftdrop.entity.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="account_action_tokens",schema="swiftdrop")
public class AccountActionToken extends CreatedEntity {
    @Column(name="user_id",nullable=false) private UUID userId;
    @Column(name="session_id",nullable=false) private UUID sessionId;
    @Column(nullable=false,length=24) private String kind;
    @Column(name="token_hash",nullable=false,length=64) private String tokenHash;
    @Column(name="authorization_version",nullable=false) private long authorizationVersion;
    @Column(name="pending_email",length=320) private String pendingEmail;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="used_at") private Instant usedAt;
    @Column(name="revoked_at") private Instant revokedAt;
    protected AccountActionToken() { }
    public AccountActionToken(UUID userId, UUID sessionId, long authorizationVersion, String kind, String tokenHash, String pendingEmail, Instant expiresAt) {
        this.userId=userId;this.sessionId=sessionId;this.authorizationVersion=authorizationVersion;this.kind=kind;
        this.tokenHash=tokenHash;this.pendingEmail=pendingEmail;this.expiresAt=expiresAt;
    }
    public UUID getUserId(){return userId;} public UUID getSessionId(){return sessionId;}
    public long getAuthorizationVersion(){return authorizationVersion;} public String getKind(){return kind;}
    public String getPendingEmail(){return pendingEmail;}
    public boolean usable(Instant now){return usedAt==null && revokedAt==null && expiresAt.isAfter(now);}
    public void use(Instant now){usedAt=now;} public void revoke(Instant now){revokedAt=now;}
}
