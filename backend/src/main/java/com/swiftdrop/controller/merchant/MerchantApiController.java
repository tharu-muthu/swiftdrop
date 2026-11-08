package com.swiftdrop.controller.merchant;

import com.swiftdrop.dto.merchant.ApiKeyVerificationResponse;
import com.swiftdrop.exception.ApiError;
import com.swiftdrop.exception.InvalidApiKeyException;
import com.swiftdrop.exception.MerchantNotApprovedException;
import com.swiftdrop.service.merchant.MerchantAccessService;
import java.util.ArrayList;
import java.util.Collections;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MerchantApiController {
    private final MerchantAccessService access;

    public MerchantApiController(MerchantAccessService access) { this.access = access; }

    /** Lets a partner confirm that their key works and see which business and scopes it carries. */
    @GetMapping("/api/v1/merchant/verify")
    public ResponseEntity<ApiKeyVerificationResponse> verify() {
        var merchant = access.requireCurrentApiMerchant();
        var scopes = new ArrayList<>(merchant.scopes());
        Collections.sort(scopes);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ApiKeyVerificationResponse(merchant.merchantId(), merchant.businessName(), scopes));
    }

    @ExceptionHandler(InvalidApiKeyException.class)
    ResponseEntity<ApiError> invalidKey() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("INVALID_API_KEY", "Invalid or revoked API key."));
    }

    @ExceptionHandler(MerchantNotApprovedException.class)
    ResponseEntity<ApiError> notApproved() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("MERCHANT_NOT_APPROVED", "This business is not approved."));
    }
}