package com.swiftdrop.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Highest precedence so the catch-all handler in ApiExceptionHandler never turns these into 500s. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PricingExceptionHandler {
    @ExceptionHandler(PricingException.class)
    ResponseEntity<ApiError> pricing(PricingException error) {
        var code = error.code();
        return ResponseEntity.status(code.status()).body(ApiError.of(code.name(), code.message()));
    }
}