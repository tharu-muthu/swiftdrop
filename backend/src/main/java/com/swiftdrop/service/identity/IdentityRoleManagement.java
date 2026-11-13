package com.swiftdrop.service.identity;

import com.swiftdrop.dto.identity.RoleAssignmentResponse;
import java.util.UUID;

/** Trusted in-process handoff. Call merchant grant within M2's approval transaction. */
public interface IdentityRoleManagement {
    RoleAssignmentResponse roleSummary(UUID targetUserId);
    void grantMerchantForApprovedOwner(UUID ownerUserId, String reason);
    void revokeMerchantForOwner(UUID ownerUserId, String reason);
    RoleAssignmentResponse grantCourier(UUID targetUserId, long expectedVersion, String reason);
}
