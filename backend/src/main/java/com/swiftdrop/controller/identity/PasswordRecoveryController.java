package com.swiftdrop.controller.identity;

import com.swiftdrop.dto.identity.PasswordResetConfirmRequest;
import com.swiftdrop.dto.identity.PasswordResetRequest;
import com.swiftdrop.service.identity.PasswordRecoveryService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;
    public PasswordRecoveryController(PasswordRecoveryService recovery) { this.recovery = recovery; }

    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> request(@Valid @RequestBody PasswordResetRequest request) {
        recovery.request(request);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(Map.of("message", "If this account can use password recovery, instructions will be sent."));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        recovery.confirm(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
