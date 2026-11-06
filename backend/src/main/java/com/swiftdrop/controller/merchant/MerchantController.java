package com.swiftdrop.controller.merchant;

import com.swiftdrop.dto.merchant.MerchantRegistrationRequest;
import com.swiftdrop.dto.merchant.MerchantResponse;
import com.swiftdrop.exception.ApiError;
import com.swiftdrop.exception.MerchantAlreadyRegisteredException;
import com.swiftdrop.service.merchant.MerchantService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MerchantController {
    private final MerchantService merchants;

    public MerchantController(MerchantService merchants) { this.merchants = merchants; }

    @PostMapping("/api/v1/merchant/registration")
    public ResponseEntity<MerchantResponse> register(@Valid @RequestBody MerchantRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(merchants.register(request));
    }

    @GetMapping("/api/v1/merchant/registration")
    public ResponseEntity<MerchantResponse> myApplication() {
        return merchants.myApplication()
                .map(found -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(found))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(MerchantAlreadyRegisteredException.class)
    ResponseEntity<ApiError> alreadyRegistered() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("MERCHANT_ALREADY_REGISTERED", "A business is already registered for this account."));
    }
}