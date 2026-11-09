package com.swiftdrop.dto.merchant;

import com.swiftdrop.entity.merchant.ApiCredential;
import java.time.Instant;
import java.util.UUID;

public record ApiKeySummary(UUID id, String keyPrefix, String scopes, Instant createdAt,
        Instant lastUsedAt, Instant revokedAt, boolean active) {
    public static ApiKeySummary from(ApiCredential credential) {
        return new ApiKeySummary(credential.getId(), credential.getKeyPrefix(), credential.getScopes(),
                credential.getCreatedAt(), credential.getLastUsedAt(), credential.getRevokedAt(),
                !credential.isRevoked());
    }
}