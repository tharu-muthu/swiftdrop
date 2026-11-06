package com.swiftdrop.dto.merchant;

import com.swiftdrop.entity.merchant.Merchant;
import java.time.Instant;
import java.util.UUID;

public record MerchantResponse(UUID id, String businessName, String contactEmail, String contactPhone,
        String businessAddress, String approvalStatus, String reviewReason, Instant createdAt) {
    public static MerchantResponse from(Merchant merchant) {
        return new MerchantResponse(merchant.getId(), merchant.getBusinessName(), merchant.getContactEmail(),
                merchant.getContactPhone(), merchant.getBusinessAddress(), merchant.getApprovalStatus().name(),
                merchant.getReviewReason(), merchant.getCreatedAt());
    }
}