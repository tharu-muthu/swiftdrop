package com.swiftdrop.service.merchant;

import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.dto.merchant.MerchantRegistrationRequest;
import com.swiftdrop.dto.merchant.MerchantResponse;
import com.swiftdrop.entity.merchant.Merchant;
import com.swiftdrop.exception.MerchantAlreadyRegisteredException;
import com.swiftdrop.repository.merchant.MerchantRepository;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantService {
    private final IdentityAccountService identities;
    private final MerchantRepository merchants;

    public MerchantService(IdentityAccountService identities, MerchantRepository merchants) {
        this.identities = identities;
        this.merchants = merchants;
    }

    @Transactional
    public MerchantResponse register(MerchantRegistrationRequest request) {
        var owner = identities.requireCurrentRole(IdentityRole.CUSTOMER);
        if (merchants.existsByOwnerUserId(owner.userId())) {
            throw new MerchantAlreadyRegisteredException();
        }
        var merchant = Merchant.register(owner.userId(), request.businessName().trim(),
                request.contactEmail().trim(), blankToNull(request.contactPhone()),
                blankToNull(request.businessAddress()));
        try {
            merchants.saveAndFlush(merchant);
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            throw new MerchantAlreadyRegisteredException();
        }
        return MerchantResponse.from(merchant);
    }

    @Transactional(readOnly = true)
    public Optional<MerchantResponse> myApplication() {
        var current = requireApplicant();
        return merchants.findByOwnerUserId(current.userId()).map(MerchantResponse::from);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    @Transactional public MerchantResponse update(com.swiftdrop.dto.merchant.MerchantProfileUpdate request) {
        var owner=requireApplicant();
        var merchant=merchants.findByOwnerUserId(owner.userId()).orElseThrow(com.swiftdrop.exception.MerchantNotFoundException::new);
        if(merchant.getVersion()!=request.expectedVersion()) throw new com.swiftdrop.exception.LifecycleConflictException("Business profile changed. Reload before saving.");
        if(!merchant.getBusinessName().equals(request.profile().businessName().trim())) throw new IllegalArgumentException("The approved business name cannot be changed through contact editing");
        merchant.updateContact(request.profile().contactEmail().trim(),blankToNull(request.profile().contactPhone()),blankToNull(request.profile().businessAddress()));
        merchants.saveAndFlush(merchant); return MerchantResponse.from(merchant);
    }
    private com.swiftdrop.security.IdentityContext requireApplicant() {
        var identity = identities.requireCurrentIdentity();
        if (!identity.roles().contains(IdentityRole.CUSTOMER.name()) && !identity.roles().contains(IdentityRole.MERCHANT.name()))
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        return identity;
    }
}
