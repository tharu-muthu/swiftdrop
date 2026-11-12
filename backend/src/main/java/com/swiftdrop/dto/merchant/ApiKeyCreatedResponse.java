package com.swiftdrop.dto.merchant;

public record ApiKeyCreatedResponse(ApiKeySummary credential, String apiKey, String notice) {
    public static ApiKeyCreatedResponse of(ApiKeySummary credential, String apiKey) {
        return new ApiKeyCreatedResponse(credential, apiKey,
                "Copy this key now. It cannot be shown again.");
    }
}