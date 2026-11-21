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
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    ResponseEntity<ApiError> missingAuthentication() {
        return ResponseEntity.status(401).body(ApiError.of("UNAUTHORIZED", "Authentication required"));
    }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<ApiError> denied() {
        return ResponseEntity.status(403).body(ApiError.of("FORBIDDEN", "Access denied"));
    }
    @ExceptionHandler(LifecycleConflictException.class)
    ResponseEntity<ApiError> lifecycleConflict(LifecycleConflictException error) {
        return ResponseEntity.status(409).body(ApiError.of("LIFECYCLE_CONFLICT", error.getMessage()));
    }
    @ExceptionHandler({ProfileConflictException.class, org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ApiError> profileConflict() {
        return ResponseEntity.status(409).body(ApiError.of("PROFILE_CONFLICT", "Your profile changed. Reload it before saving again."));
    }
    @ExceptionHandler(WebhookEndpointException.class)
    ResponseEntity<ApiError> webhookEndpoint() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_WEBHOOK_ENDPOINT", "The webhook endpoint is invalid."));
    }
    @ExceptionHandler(WebhookSubscriptionConflictException.class)
    ResponseEntity<ApiError> webhookConflict() {
        return ResponseEntity.status(409).body(ApiError.of("WEBHOOK_SUBSCRIPTION_CONFLICT", "A webhook subscription already uses this endpoint."));
    }
    @ExceptionHandler(WebhookSubscriptionNotFoundException.class)
    ResponseEntity<ApiError> webhookNotFound() {
        return ResponseEntity.status(404).body(ApiError.of("WEBHOOK_NOT_FOUND", "Webhook subscription not found."));
    }
    @ExceptionHandler(MerchantNotApprovedException.class)
    ResponseEntity<ApiError> merchantNotApproved() {
        return ResponseEntity.status(403).body(ApiError.of("MERCHANT_NOT_APPROVED", "This business is not approved."));
    }
    @ExceptionHandler(MerchantNotFoundException.class)
    ResponseEntity<ApiError> merchantNotFound() {
        return ResponseEntity.status(404).body(ApiError.of("MERCHANT_NOT_FOUND", "Merchant application not found."));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalidArgument() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", "The request is invalid."));
    }
    @ExceptionHandler(InvalidResetTokenException.class)
    ResponseEntity<ApiError> invalidResetToken() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_RESET_TOKEN", "This reset link is invalid or has expired. Request a new link."));
    }
    @ExceptionHandler(InvalidVerificationTokenException.class)
    ResponseEntity<ApiError> invalidVerificationToken() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_VERIFICATION_TOKEN", "This verification link is invalid or has expired. Request a new link."));
    }
    @ExceptionHandler(IdentityAuthenticationException.class)
    ResponseEntity<ApiError> invalidIdentity(jakarta.servlet.http.HttpServletRequest request) {
        boolean login = request.getRequestURI().equals("/api/v1/auth/login");
        return ResponseEntity.status(401).body(ApiError.of(login ? "INVALID_CREDENTIALS" : "UNAUTHORIZED",
                login ? "Email or password is incorrect." : "Your session has ended. Please sign in again."));
    }
    @ExceptionHandler(EmailVerificationRequiredException.class)
    ResponseEntity<ApiError> emailVerificationRequired() {
        return ResponseEntity.status(403).body(ApiError.of("EMAIL_VERIFICATION_REQUIRED",
                "Verify your email before logging in. Check your inbox or request a new link."));
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
        String path = request.getRequestURI();
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST",
                path.equals("/api/v1/me")
                        ? "Send a JSON object containing only fullName, phone and version."
                        : path.equals("/api/v1/auth/password-reset/request")
                        ? "Send a JSON object containing only email."
                        : path.equals("/api/v1/auth/password-reset/confirm")
                        ? "Send a JSON object containing only token and password."
                        : path.equals("/api/v1/auth/email-verification/request")
                        ? "Send a JSON object containing only email."
                        : path.equals("/api/v1/auth/email-verification/confirm")
                        ? "Send a JSON object containing only token."
                        : path.equals("/api/v1/auth/login")
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
