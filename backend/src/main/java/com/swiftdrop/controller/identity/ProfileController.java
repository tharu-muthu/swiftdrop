package com.swiftdrop.controller.identity;

import com.swiftdrop.dto.identity.UserProfile;
import com.swiftdrop.dto.identity.ProfileUpdateRequest;
import com.swiftdrop.security.IdentityContext;
import com.swiftdrop.service.identity.IdentitySessionService;
import com.swiftdrop.service.identity.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProfileController {
    private final IdentitySessionService sessions;
    private final ProfileService profiles;
    public ProfileController(IdentitySessionService sessions, ProfileService profiles) {
        this.sessions = sessions; this.profiles = profiles;
    }
    @GetMapping("/api/v1/me") public ResponseEntity<UserProfile> me(@AuthenticationPrincipal IdentityContext identity) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.profile(identity));
    }
    @PatchMapping("/api/v1/me") public ResponseEntity<UserProfile> update(
            @AuthenticationPrincipal IdentityContext identity, @Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.update(identity, request));
    }
}
