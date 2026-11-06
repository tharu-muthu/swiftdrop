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
        var current = identities.requireCurrentIdentity();
        return merchants.findByOwnerUserId(current.userId()).map(MerchantResponse::from);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}