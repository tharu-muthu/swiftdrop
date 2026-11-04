package com.swiftdrop.integration.identity;

import com.swiftdrop.config.AuthSettings;
import com.swiftdrop.config.GoogleSettings;
import com.swiftdrop.controller.identity.SessionController;
import com.swiftdrop.service.identity.GoogleOAuthService;
import com.swiftdrop.service.identity.IdentitySessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

@Component
public class GoogleCallbackHandler implements AuthenticationSuccessHandler {

    private final GoogleOAuthService googleOAuth;
    private final AuthSettings authSettings;
    private final GoogleSettings googleSettings;
    private final Clock clock;

    public GoogleCallbackHandler(GoogleOAuthService googleOAuth, AuthSettings authSettings,
            GoogleSettings googleSettings, Clock clock) {
        this.googleOAuth = googleOAuth;
        this.authSettings = authSettings;
        this.googleSettings = googleSettings;
        this.clock = clock;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        GoogleOAuthService.Result result = googleOAuth.handleCallback(oidcUser);

        // Consume and discard the OIDC framework session — state/nonce no longer needed.
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();

        String frontendOrigin = googleSettings.frontendOrigin();

        switch (result.outcome()) {
            case SUCCESS -> {
                IdentitySessionService.SessionTokens tokens = result.tokens();
                Duration remaining = Duration.between(clock.instant(), tokens.expiresAt());
                ResponseCookie cookie = ResponseCookie.from(SessionController.REFRESH_COOKIE, tokens.refresh())
                        .httpOnly(true)
                        .secure(!authSettings.localhostDevelopment())
                        .sameSite("Lax")
                        .path("/api/v1/auth")
                        .maxAge(remaining.isNegative() ? Duration.ZERO : remaining)
                        .build();
                response.addHeader("Set-Cookie", cookie.toString());
                response.sendRedirect(frontendOrigin + "/auth/complete");
            }
            case SUSPENDED -> response.sendRedirect(frontendOrigin + "/login?error=suspended");
            case EMAIL_COLLISION -> response.sendRedirect(frontendOrigin + "/login?error=link-required");
            case UNVERIFIED_EMAIL -> response.sendRedirect(frontendOrigin + "/login?error=unverified-email");
        }
    }
}

