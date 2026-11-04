package com.swiftdrop.service.identity;

import com.swiftdrop.entity.identity.*;
import com.swiftdrop.repository.identity.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class GoogleOAuthService {

    private static final String PROVIDER = "google";

    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository memberships;
    private final OAuthAccountRepository oauthAccounts;
    private final IdentitySessionService sessionService;

    public GoogleOAuthService(UserRepository users, RoleRepository roles,
            UserRoleRepository memberships, OAuthAccountRepository oauthAccounts,
            IdentitySessionService sessionService) {
        this.users = users;
        this.roles = roles;
        this.memberships = memberships;
        this.oauthAccounts = oauthAccounts;
        this.sessionService = sessionService;
    }

    public enum Outcome { SUCCESS, SUSPENDED, EMAIL_COLLISION, UNVERIFIED_EMAIL }

    public record Result(Outcome outcome, IdentitySessionService.SessionTokens tokens) {}

    @Transactional
    public Result handleCallback(OidcUser oidcUser) {
        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            return new Result(Outcome.UNVERIFIED_EMAIL, null);
        }

        String subject = oidcUser.getSubject();
        String rawEmail = oidcUser.getEmail();
        String fullName = oidcUser.getFullName() != null ? oidcUser.getFullName() : rawEmail;

        Optional<OAuthAccount> existingLink = oauthAccounts.findByProviderAndProviderSubject(PROVIDER, subject);

        User user;
        if (existingLink.isPresent()) {
            // Returning Google user
            user = users.lockById(existingLink.get().getUserId())
                    .orElseThrow(() -> new IllegalStateException("Linked user not found"));
        } else {
            // New Google login — check for email collision with a password account
            String normalized = users.normalizeEmail(rawEmail);
            Optional<User> collision = users.findByEmailNormalized(normalized);
            if (collision.isPresent()) {
                // Do NOT auto-link. Require explicit linking.
                return new Result(Outcome.EMAIL_COLLISION, null);
            }
            // Create new CUSTOMER (Google-only, null password hash)
            User newUser = users.saveAndFlush(User.registerGoogleCustomer(fullName, rawEmail));
            var customerRole = roles.findByName("CUSTOMER")
                    .orElseThrow(() -> new IllegalStateException("CUSTOMER role missing"));
            memberships.saveAndFlush(new UserRole(newUser.getId(), customerRole.getId()));
            oauthAccounts.saveAndFlush(new OAuthAccount(newUser.getId(), PROVIDER, subject));
            // Re-lock for session creation
            user = users.lockById(newUser.getId()).orElseThrow();
        }

        if (!user.isActive()) {
            return new Result(Outcome.SUSPENDED, null);
        }

        IdentitySessionService.SessionTokens tokens = sessionService.createSession(user);
        return new Result(Outcome.SUCCESS, tokens);
    }
}

