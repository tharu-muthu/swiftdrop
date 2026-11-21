package com.swiftdrop.dto.merchant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record MerchantProfileUpdate(@NotNull @Valid MerchantRegistrationRequest profile,@PositiveOrZero long expectedVersion) { }
