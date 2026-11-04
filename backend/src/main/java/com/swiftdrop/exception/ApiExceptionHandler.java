package com.swiftdrop.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Comparator;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IdentityAuthenticationException.class)
    ResponseEntity<ApiError> invalidIdentity(jakarta.servlet.http.HttpServletRequest request) {
        boolean login = request.getRequestURI().equals("/api/v1/auth/login");
        return ResponseEntity.status(401).body(ApiError.of(login ? "INVALID_CREDENTIALS" : "UNAUTHORIZED",
                login ? "Email or password is incorrect." : "Your session has ended. Please sign in again."));
    }
    @ExceptionHandler(RateLimitException.class)
    ResponseEntity<ApiError> throttled(RateLimitException error) {
        return ResponseEntity.status(429).header("Retry-After", Long.toString(error.retryAfter()))
                .body(ApiError.of("RATE_LIMITED", "Too many attempts. Please try again later."));
    }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedContent() {
        return ResponseEntity.status(415).body(ApiError.of("UNSUPPORTED_MEDIA_TYPE", "Use application/json."));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException error) {
        var fields = error.getBindingResult().getFieldErrors().stream()
                .map(field -> new ApiError.FieldError(field.getField(), field.getDefaultMessage()))
                .distinct().sorted(Comparator.comparing(ApiError.FieldError::field)).toList();
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "Check the highlighted fields.",
                UUID.randomUUID().toString(), fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST",
                request.getRequestURI().equals("/api/v1/auth/login")
                        ? "Send a JSON object containing only email and password."
                        : "Send a JSON object containing only fullName, email and password."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraint
                    && "uq_users_email_normalized".equals(constraint.getConstraintName())) {
                return ResponseEntity.status(409).body(ApiError.of("REGISTRATION_UNAVAILABLE",
                        "An account could not be created with these details."));
            }
        }
        return internal();
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> internal() {
        // Never serialize/log exception text: SQL detail may contain an entire credential-bearing row.
        return ResponseEntity.internalServerError().body(ApiError.of("INTERNAL_ERROR", "Unable to complete the request."));
    }
}
