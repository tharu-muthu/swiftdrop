package com.swiftdrop.dto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CourierRoleRequest(@NotBlank @Size(max = 500) String reason,
        @NotNull @PositiveOrZero Long expectedVersion) { }
