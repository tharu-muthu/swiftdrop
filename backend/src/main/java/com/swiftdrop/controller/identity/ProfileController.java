package com.swiftdrop.controller.identity;

import com.swiftdrop.dto.identity.UserProfile;
import com.swiftdrop.security.IdentityContext;
import com.swiftdrop.service.identity.IdentitySessionService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProfileController {
    private final IdentitySessionService sessions;
    public ProfileController(IdentitySessionService sessions) { this.sessions = sessions; }
    @GetMapping("/api/v1/me") public ResponseEntity<UserProfile> me(@AuthenticationPrincipal IdentityContext identity) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.profile(identity));
    }
}
