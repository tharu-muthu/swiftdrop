package com.swiftdrop.integration.identity;

import com.swiftdrop.config.GoogleSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GoogleFailureHandler implements AuthenticationFailureHandler {

    private final GoogleSettings googleSettings;

    public GoogleFailureHandler(GoogleSettings googleSettings) {
        this.googleSettings = googleSettings;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        // Discard the OIDC session on failure too.
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        response.sendRedirect(googleSettings.frontendOrigin() + "/login?error=google-failed");
    }
}

