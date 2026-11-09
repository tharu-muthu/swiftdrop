package com.swiftdrop.dto.merchant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MerchantRejectionRequest(@NotBlank @Size(max = 500) String reason) { }