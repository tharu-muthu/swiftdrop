package com.swiftdrop.dto.merchant;

import java.util.List;

public record MerchantPageResponse(List<MerchantAdminResponse> items, int page, int size,
        long totalItems, int totalPages) { }