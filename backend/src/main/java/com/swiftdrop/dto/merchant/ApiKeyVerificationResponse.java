package com.swiftdrop.dto.merchant;

import java.util.List;
import java.util.UUID;

public record ApiKeyVerificationResponse(UUID merchantId, String businessName, List<String> scopes) { }