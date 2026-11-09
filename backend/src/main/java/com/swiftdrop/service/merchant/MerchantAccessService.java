package com.swiftdrop.service.merchant;

import com.swiftdrop.dto.merchant.MerchantContext;
import com.swiftdrop.entity.merchant.Merchant;
import com.swiftdrop.entity.merchant.MerchantApprovalStatus;
import com.swiftdrop.exception.InvalidApiKeyException;
import com.swiftdrop.exception.MerchantNotApprovedException;
import com.swiftdrop.exception.MerchantNotFoundException;
import com.swiftdrop.repository.merchant.ApiCredentialRepository;
import com.swiftdrop.repository.merchant.MerchantRepository;
import com.swiftdrop.security.MerchantApiPrincipal;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Published contract for M4 and M9: resolve the approved merchant for the current request. */
@Service
public class MerchantAccessService {
    private final IdentityAccountService identities;
    private final MerchantRepository merchants;
    private final ApiCredentialRepository credentials;

    public MerchantAccessService(IdentityAccountService identities, MerchantRepository merchants,
            ApiCredentialRepository credentials) {
        this.identities = identities;
        this.merchants = merchants;
        this.credentials = credentials;
    }

    /** For partner API requests authenticated by an API key. Re-checks live key and approval state. */
    @Transactional(readOnly = true)
    public MerchantContext requireCurrentApiMerchant() {
        var principal = currentApiPrincipal();
        var credential = credentials.findById(principal.credentialId())
                .filter(found -> !found.isRevoked())
                .orElseThrow(InvalidApiKeyException::new);
        var merchant = requireApproved(merchants.findById(credential.getMerchantId())
                .orElseThrow(InvalidApiKeyException::new));
        return new MerchantContext(credential.getMerchantId(), merchant.getOwnerUserId(),
                merchant.getBusinessName(), principal.credentialId(), credential.scopeSet());
    }

    /** For signed-in owners (dashboard, webhook settings). The merchant comes from the session, never the body. */
    @Transactional(readOnly = true)
    public MerchantContext requireApprovedOwnerMerchant() {
        var owner = identities.requireCurrentIdentity();
        var merchant = requireApproved(merchants.findByOwnerUserId(owner.userId())
                .orElseThrow(MerchantNotFoundException::new));
        return new MerchantContext(merchant.getId(), merchant.getOwnerUserId(),
                merchant.getBusinessName(), null, Set.of());
    }

    private MerchantApiPrincipal currentApiPrincipal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof MerchantApiPrincipal principal) {
            return principal;
        }
        throw new InvalidApiKeyException();
    }

    private Merchant requireApproved(Merchant merchant) {
        if (merchant.getApprovalStatus() != MerchantApprovalStatus.APPROVED) {
            throw new MerchantNotApprovedException();
        }
        return merchant;
    }
}