package com.swiftdrop.controller.identity;

import com.swiftdrop.config.AuthSettings;
import com.swiftdrop.dto.identity.*;
import com.swiftdrop.exception.ApiError;
import com.swiftdrop.service.identity.IdentitySessionService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class SessionController {
    public static final String REFRESH_COOKIE = "swiftdrop_refresh";
    private final IdentitySessionService sessions;
    private final AuthSettings settings;
    private final Clock clock;
    public SessionController(IdentitySessionService sessions, AuthSettings settings, Clock clock) {
        this.sessions = sessions; this.settings = settings; this.clock = clock;
    }
    private ResponseCookie cookie(String value, Duration age) {
        return ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(!settings.localhostDevelopment())
                .sameSite("Lax").path("/api/v1/auth").maxAge(age).build();
    }
    private ResponseEntity<TokenResponse> success(IdentitySessionService.SessionTokens tokens) {
        Duration remaining = Duration.between(clock.instant(), tokens.expiresAt());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(tokens.refresh(), remaining.isNegative() ? Duration.ZERO : remaining).toString())
                .body(tokens.access());
    }
    @PostMapping("/login") public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return success(sessions.login(request));
    }
    @PostMapping("/refresh") public ResponseEntity<?> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String raw) {
        var tokens = sessions.refresh(raw);
        if (tokens != null) return success(tokens);
        return ResponseEntity.status(401).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .body(ApiError.of("SESSION_EXPIRED", "Your session has ended. Please sign in again."));
    }
    @PostMapping("/logout") public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String raw) {
        sessions.logout(raw);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }
}
