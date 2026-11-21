package com.swiftdrop.dto.merchant;

import java.util.List;

public record MerchantOrderPage(List<MerchantOrderResponse> items, int page, int size,
        int totalPages, long totalElements) { }
