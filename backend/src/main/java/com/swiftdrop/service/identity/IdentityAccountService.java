package com.swiftdrop.service.identity;

import com.swiftdrop.dto.identity.AccountSummary;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.security.IdentityContext;
import java.util.UUID;

/** Request-bound in-process contract. Never accept an actor identity from client input. */
public interface IdentityAccountService {
    IdentityContext requireCurrentIdentity();
    IdentityContext requireCurrentRole(IdentityRole role);
    /** Only an active DISPATCHER_ADMIN may inspect a courier for dispatch. */
    AccountSummary requireActiveCourier(UUID userId);
    /** Admin-only bulk eligibility lookup for M5; never relies on cached role claims. */
    java.util.Set<UUID> eligibleCourierIds(java.util.Collection<UUID> candidateIds);
    /** Trusted merchant integration handoff: verify the current state of an API-key owner. */
    void requireActiveMerchant(UUID userId);
    boolean isActiveMerchantAccount(UUID userId);
}
