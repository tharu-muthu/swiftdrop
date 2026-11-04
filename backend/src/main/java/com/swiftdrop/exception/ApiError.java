package com.swiftdrop.exception;

import java.util.List;
import java.util.UUID;

public record ApiError(String code, String message, String correlationId, List<FieldError> fieldErrors) {
    public record FieldError(String field, String message) { }
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, UUID.randomUUID().toString(), List.of());
    }
}
