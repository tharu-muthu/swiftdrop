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
}
