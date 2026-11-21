package com.swiftdrop.controller.identity;

import com.swiftdrop.service.identity.GoogleLinkService;
import com.swiftdrop.security.AuthRateLimiter;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="swiftdrop.google",name="enabled",havingValue="true")
public class GoogleLinkController {
    public static final String INTENT="swiftdrop.google.link";
    private final GoogleLinkService links; private final AuthRateLimiter limiter;
    public GoogleLinkController(GoogleLinkService links,AuthRateLimiter limiter) { this.links=links; this.limiter=limiter; }
    public record LinkRequest(@NotBlank @Size(max=256) String password) { @Override public String toString() { return "LinkRequest[redacted]"; } }
    @PostMapping("/api/v1/me/google-link") public Map<String,String> begin(@Valid @RequestBody LinkRequest body,HttpServletRequest request) {
        limiter.requireAllowed("google-link:"+request.getRemoteAddr(),5,Duration.ofMinutes(10));
        var intent=links.begin(body.password());
        var previous=request.getSession(false); if(previous!=null) previous.invalidate();
        var session=request.getSession(true); session.setMaxInactiveInterval(300); session.setAttribute(INTENT,intent);
        return Map.of("authorizationUrl","/oauth2/authorization/google");
    }
}
