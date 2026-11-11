package com.swiftdrop.controller.merchant;

import com.swiftdrop.dto.merchant.ApiKeyCreatedResponse;
import com.swiftdrop.dto.merchant.ApiKeySummary;
import com.swiftdrop.exception.ApiError;
import com.swiftdrop.exception.ApiKeyLimitException;
import com.swiftdrop.exception.ApiKeyNotFoundException;
import com.swiftdrop.exception.MerchantNotApprovedException;
import com.swiftdrop.exception.MerchantNotFoundException;
import com.swiftdrop.service.merchant.ApiKeyService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
public class ApiKeyController {
    private final ApiKeyService apiKeys;

    public ApiKeyController(ApiKeyService apiKeys) { this.apiKeys = apiKeys; }

    @PostMapping("/api/v1/merchant/api-keys")
    public ResponseEntity<ApiKeyCreatedResponse> create() {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(apiKeys.create());
    }

    @GetMapping("/api/v1/merchant/api-keys")
    public ResponseEntity<List<ApiKeySummary>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(apiKeys.list());
    }

    @PostMapping("/api/v1/merchant/api-keys/{id}/revoke")
    public ResponseEntity<ApiKeySummary> revoke(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(apiKeys.revoke(id));
    }

    @ExceptionHandler(MerchantNotFoundException.class)
    ResponseEntity<ApiError> noMerchant() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("MERCHANT_NOT_FOUND", "Register a business first."));
    }

    @ExceptionHandler(MerchantNotApprovedException.class)
    ResponseEntity<ApiError> notApproved() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("MERCHANT_NOT_APPROVED", "Your business is not approved yet."));
    }

    @ExceptionHandler(ApiKeyNotFoundException.class)
    ResponseEntity<ApiError> noKey() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("API_KEY_NOT_FOUND", "API key not found."));
    }

    @ExceptionHandler(ApiKeyLimitException.class)
    ResponseEntity<ApiError> limit() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("API_KEY_LIMIT_REACHED", "Revoke an existing key before creating another."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> badId() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", "Invalid key id."));
    }
}