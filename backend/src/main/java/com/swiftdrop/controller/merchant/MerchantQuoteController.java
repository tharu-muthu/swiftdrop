package com.swiftdrop.controller.merchant;

import com.swiftdrop.dto.pricing.QuoteRequest;
import com.swiftdrop.dto.pricing.QuoteResponse;
import com.swiftdrop.exception.ApiError;
import com.swiftdrop.exception.InvalidApiKeyException;
import com.swiftdrop.exception.MerchantNotApprovedException;
import com.swiftdrop.service.merchant.MerchantQuoteService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
public class MerchantQuoteController {
    private final MerchantQuoteService quotes;

    public MerchantQuoteController(MerchantQuoteService quotes) { this.quotes = quotes; }

    @PostMapping("/api/v1/merchant/quotes")
    public ResponseEntity<QuoteResponse> create(@Valid @RequestBody QuoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(quotes.create(request));
    }

    @GetMapping("/api/v1/merchant/quotes/{quoteId}")
    public ResponseEntity<QuoteResponse> get(@PathVariable UUID quoteId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quotes.get(quoteId));
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> badId() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", "Invalid quote id."));
    }
}