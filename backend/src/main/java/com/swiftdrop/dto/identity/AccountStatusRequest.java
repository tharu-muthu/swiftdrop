package com.swiftdrop.dto.identity;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AccountStatusRequest(@NotBlank @Size(max = 500) String reason,
        @NotNull @PositiveOrZero Long expectedVersion) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Only reason and expectedVersion are accepted.");
    }
}
