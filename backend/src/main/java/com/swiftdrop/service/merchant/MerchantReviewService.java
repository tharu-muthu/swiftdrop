package com.swiftdrop.service.merchant;

import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.dto.merchant.MerchantAdminResponse;
import com.swiftdrop.dto.merchant.MerchantPageResponse;
import com.swiftdrop.entity.merchant.Merchant;
import com.swiftdrop.entity.merchant.MerchantApprovalStatus;
import com.swiftdrop.exception.MerchantDecisionConflictException;
import com.swiftdrop.exception.MerchantNotFoundException;
import com.swiftdrop.repository.merchant.MerchantRepository;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantReviewService {
    static final int MAX_PAGE_SIZE = 100;

    private final IdentityAccountService identities;
    private final MerchantRepository merchants;

    public MerchantReviewService(IdentityAccountService identities, MerchantRepository merchants) {
        this.identities = identities;
        this.merchants = merchants;
    }

    @Transactional(readOnly = true)
    public MerchantPageResponse list(MerchantApprovalStatus status, int page, int size) {
        identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        var paging = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("createdAt").ascending());
        Page<Merchant> result = status == null ? merchants.findAll(paging)
                : merchants.findByApprovalStatus(status, paging);
        return new MerchantPageResponse(result.getContent().stream().map(MerchantAdminResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public MerchantAdminResponse approve(UUID merchantId) {
        var admin = identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        var merchant = loadPending(merchantId);
        merchant.approve(admin.userId(), Instant.now());
        merchants.saveAndFlush(merchant);
        return MerchantAdminResponse.from(merchant);
    }

    @Transactional
    public MerchantAdminResponse reject(UUID merchantId, String reason) {
        var admin = identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        var merchant = loadPending(merchantId);
        merchant.reject(admin.userId(), reason.trim(), Instant.now());
        merchants.saveAndFlush(merchant);
        return MerchantAdminResponse.from(merchant);
    }

    private Merchant loadPending(UUID merchantId) {
        var merchant = merchants.findForUpdate(merchantId).orElseThrow(MerchantNotFoundException::new);
        if (!merchant.isPending()) {
            throw new MerchantDecisionConflictException();
        }
        return merchant;
    }
}