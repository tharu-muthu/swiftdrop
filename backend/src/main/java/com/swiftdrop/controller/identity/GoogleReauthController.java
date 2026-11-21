package com.swiftdrop.controller.identity;
import com.swiftdrop.security.IdentityContext;
import com.swiftdrop.service.identity.AccountSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnProperty(prefix="swiftdrop.google",name="enabled",havingValue="true")
public class GoogleReauthController {
    public static final String INTENT="swiftdrop.google.security-reauth";
    private final AccountSecurityService security;
    public GoogleReauthController(AccountSecurityService security){this.security=security;}
    @PostMapping("/api/v1/me/security/google-reauth")
    public ResponseEntity<Map<String,String>> begin(@AuthenticationPrincipal IdentityContext identity,HttpServletRequest request){
        var intent=security.beginGoogle(identity);
        var old=request.getSession(false);if(old!=null)old.invalidate();
        var session=request.getSession(true);session.setMaxInactiveInterval(300);session.setAttribute(INTENT,intent);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("authorizationUrl","/oauth2/authorization/google"));
    }
}
