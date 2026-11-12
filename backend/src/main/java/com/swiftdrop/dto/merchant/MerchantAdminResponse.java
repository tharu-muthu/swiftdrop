package com.swiftdrop.dto.merchant;

import com.swiftdrop.entity.merchant.Merchant;
import java.time.Instant;
import java.util.UUID;

public record MerchantAdminResponse(UUID id, UUID ownerUserId, String businessName, String contactEmail,
        String contactPhone, String businessAddress, String approvalStatus, String reviewReason,
        Instant reviewedAt, Instant createdAt) {
    public static MerchantAdminResponse from(Merchant m) {
        return new MerchantAdminResponse(m.getId(), m.getOwnerUserId(), m.getBusinessName(), m.getContactEmail(),
                m.getContactPhone(), m.getBusinessAddress(), m.getApprovalStatus().name(), m.getReviewReason(),
                m.getReviewedAt(), m.getCreatedAt());
    }
}