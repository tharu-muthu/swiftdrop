package com.swiftdrop.dto.merchant;

import java.util.Set;
import java.util.UUID;

/** Who the merchant is for the current request. credentialId is null for signed-in owner requests. */
public record MerchantContext(UUID merchantId, UUID ownerUserId, String businessName,
        UUID credentialId, Set<String> scopes) { }