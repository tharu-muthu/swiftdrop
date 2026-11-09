package com.swiftdrop.service.merchant;

import com.swiftdrop.dto.merchant.ApiKeyCreatedResponse;
import com.swiftdrop.dto.merchant.ApiKeySummary;
import com.swiftdrop.entity.merchant.ApiCredential;
import com.swiftdrop.entity.merchant.Merchant;
import com.swiftdrop.entity.merchant.MerchantApprovalStatus;
import com.swiftdrop.exception.ApiKeyLimitException;
import com.swiftdrop.exception.ApiKeyNotFoundException;
import com.swiftdrop.exception.MerchantNotApprovedException;
import com.swiftdrop.exception.MerchantNotFoundException;
import com.swiftdrop.repository.merchant.ApiCredentialRepository;
import com.swiftdrop.repository.merchant.MerchantRepository;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyService {
    static final int MAX_ACTIVE_KEYS = 5;
    static final String DEFAULT_SCOPES = "quote,order";

    private final IdentityAccountService identities;
    private final MerchantRepository merchants;
    private final ApiCredentialRepository credentials;
    private final ApiKeyGenerator generator;

    public ApiKeyService(IdentityAccountService identities, MerchantRepository merchants,
            ApiCredentialRepository credentials, ApiKeyGenerator generator) {
        this.identities = identities;
        this.merchants = merchants;
        this.credentials = credentials;
        this.generator = generator;
    }

    @Transactional
    public ApiKeyCreatedResponse create() {
        var owner = identities.requireCurrentIdentity();
        var found = merchants.findByOwnerUserId(owner.userId()).orElseThrow(MerchantNotFoundException::new);
        // Lock the merchant row so concurrent requests cannot exceed the active-key limit.
        Merchant merchant = merchants.findForUpdate(found.getId()).orElseThrow(MerchantNotFoundException::new);
        if (merchant.getApprovalStatus() != MerchantApprovalStatus.APPROVED) {
            throw new MerchantNotApprovedException();
        }
        if (credentials.countByMerchantIdAndRevokedAtIsNull(merchant.getId()) >= MAX_ACTIVE_KEYS) {
            throw new ApiKeyLimitException();
        }
        var generated = generator.generate();
        for (int attempt = 0; attempt < 3 && credentials.existsByKeyPrefix(generated.prefix()); attempt++) {
            generated = generator.generate();
        }
        var credential = ApiCredential.issue(merchant.getId(), generated.prefix(), generated.hash(), DEFAULT_SCOPES);
        credentials.saveAndFlush(credential);
        return ApiKeyCreatedResponse.of(ApiKeySummary.from(credential), generated.key());
    }

    @Transactional(readOnly = true)
    public List<ApiKeySummary> list() {
        var merchant = ownMerchant();
        return credentials.findByMerchantIdOrderByCreatedAtDesc(merchant.getId()).stream()
                .map(ApiKeySummary::from).toList();
    }

    @Transactional
    public ApiKeySummary revoke(UUID credentialId) {
        var merchant = ownMerchant();
        var credential = credentials.findByIdAndMerchantId(credentialId, merchant.getId())
                .orElseThrow(ApiKeyNotFoundException::new);
        credential.revoke(Instant.now());
        credentials.saveAndFlush(credential);
        return ApiKeySummary.from(credential);
    }

    private Merchant ownMerchant() {
        var owner = identities.requireCurrentIdentity();
        return merchants.findByOwnerUserId(owner.userId()).orElseThrow(MerchantNotFoundException::new);
    }
}