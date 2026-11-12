package com.swiftdrop.controller.identity;

import com.swiftdrop.dto.identity.EmailVerificationConfirmRequest;
import com.swiftdrop.dto.identity.EmailVerificationRequest;
import com.swiftdrop.service.identity.EmailVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/email-verification")
public class EmailVerificationController {
    private final EmailVerificationService verification;
    public EmailVerificationController(EmailVerificationService verification) { this.verification = verification; }

    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> request(@Valid @RequestBody EmailVerificationRequest request) {
        verification.request(request);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(Map.of("message", "If this account needs email verification, a link will be sent."));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        verification.confirm(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
