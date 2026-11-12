package com.swiftdrop.controller.merchant;

import com.swiftdrop.dto.merchant.MerchantAdminResponse;
import com.swiftdrop.dto.merchant.MerchantPageResponse;
import com.swiftdrop.dto.merchant.MerchantRejectionRequest;
import com.swiftdrop.entity.merchant.MerchantApprovalStatus;
import com.swiftdrop.exception.ApiError;
import com.swiftdrop.exception.MerchantDecisionConflictException;
import com.swiftdrop.exception.MerchantNotFoundException;
import com.swiftdrop.service.merchant.MerchantReviewService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
public class MerchantAdminController {
    private final MerchantReviewService reviews;

    public MerchantAdminController(MerchantReviewService reviews) { this.reviews = reviews; }

    @GetMapping("/api/v1/admin/merchants")
    public ResponseEntity<MerchantPageResponse> list(
            @RequestParam(required = false) MerchantApprovalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.list(status, page, size));
    }

    @PostMapping("/api/v1/admin/merchants/{id}/approve")
    public ResponseEntity<MerchantAdminResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.approve(id));
    }

    @PostMapping("/api/v1/admin/merchants/{id}/reject")
    public ResponseEntity<MerchantAdminResponse> reject(@PathVariable UUID id,
            @Valid @RequestBody MerchantRejectionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.reject(id, request.reason()));
    }

    @ExceptionHandler(MerchantNotFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("MERCHANT_NOT_FOUND", "Merchant application not found."));
    }

    @ExceptionHandler(MerchantDecisionConflictException.class)
    ResponseEntity<ApiError> alreadyDecided() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("MERCHANT_ALREADY_DECIDED", "This application has already been decided."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> badParameter() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", "Invalid id or status value."));
    }
}