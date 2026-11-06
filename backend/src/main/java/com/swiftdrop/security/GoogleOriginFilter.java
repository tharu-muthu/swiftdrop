package com.swiftdrop.security;

import com.swiftdrop.config.GoogleSettings;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import org.springframework.web.filter.OncePerRequestFilter;

/** Establish OAuth state only on the same origin that receives its callback cookie. */
final class GoogleOriginFilter extends OncePerRequestFilter {
    private final URI origin;
    GoogleOriginFilter(GoogleSettings settings) { origin = URI.create(settings.frontendOrigin()); }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        int port = origin.getPort() < 0 ? ("https".equals(origin.getScheme()) ? 443 : 80) : origin.getPort();
        boolean canonical = origin.getScheme().equalsIgnoreCase(request.getScheme())
                && origin.getHost().equalsIgnoreCase(request.getServerName()) && port == request.getServerPort();
        if (!canonical && "/oauth2/authorization/google".equals(request.getServletPath())) {
            response.setHeader("Cache-Control", "no-store");
            response.sendRedirect(origin + "/oauth2/authorization/google");
            return;
        }
        if (!canonical && request.getServletPath().startsWith("/login/oauth2/")) {
            response.sendError(400, "OAuth callback origin mismatch");
            return;
        }
        chain.doFilter(request, response);
    }
}
