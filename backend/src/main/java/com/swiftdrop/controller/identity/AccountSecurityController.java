package com.swiftdrop.controller.identity;
import com.swiftdrop.dto.identity.AccountSecurityRequests.*;
import com.swiftdrop.security.IdentityContext;
import com.swiftdrop.service.identity.AccountSecurityService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class AccountSecurityController {
    private final AccountSecurityService service;
    private final com.swiftdrop.config.AuthSettings settings;
    public AccountSecurityController(AccountSecurityService service,com.swiftdrop.config.AuthSettings settings){this.service=service;this.settings=settings;}
    private String clearRefresh(){
        return ResponseCookie.from(SessionController.REFRESH_COOKIE,"").path("/api/v1/auth").httpOnly(true)
            .secure(!settings.localhostDevelopment()).sameSite("Lax").maxAge(java.time.Duration.ZERO).build().toString();
    }
    @PostMapping("/api/v1/me/security/email-change")
    public ResponseEntity<Void> request(@AuthenticationPrincipal IdentityContext identity,@Valid @RequestBody ChangeEmail request){
        service.requestEmail(identity,request);return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/api/v1/auth/email-change/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody ConfirmEmail request){
        service.confirmEmail(request.token());return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,clearRefresh()).cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/api/v1/me/security/password")
    public ResponseEntity<Void> password(@AuthenticationPrincipal IdentityContext identity,@Valid @RequestBody ChangePassword request){
        service.changePassword(identity,request);return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,clearRefresh()).cacheControl(CacheControl.noStore()).build();
    }
}
