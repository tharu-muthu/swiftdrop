package com.swiftdrop.service.identity;

import com.swiftdrop.config.AuthSettings;
import com.swiftdrop.dto.identity.*;
import com.swiftdrop.entity.identity.*;
import com.swiftdrop.exception.IdentityAuthenticationException;
import com.swiftdrop.repository.identity.*;
import com.swiftdrop.security.AuthRateLimiter;
import com.swiftdrop.security.IdentityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;

@Service
public class IdentitySessionService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final OAuthAccountRepository oauthAccounts;
    private final AuthSessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final AccessTokenService accessTokens;
    private final AuthSettings settings;
    private final Clock clock;
    private final AuthRateLimiter limiter;
    private final int accountLimit;
    private final String dummyHash;

    public IdentitySessionService(UserRepository users, RoleRepository roles, OAuthAccountRepository oauthAccounts, AuthSessionRepository sessions,
            RefreshTokenRepository refreshTokens, PasswordEncoder passwords, AccessTokenService accessTokens,
            AuthSettings settings, Clock clock, AuthRateLimiter limiter,
            @Value("${swiftdrop.auth.account-login-limit:10}") int accountLimit) {
        this.users = users; this.roles = roles; this.oauthAccounts = oauthAccounts; this.sessions = sessions; this.refreshTokens = refreshTokens;
        this.passwords = passwords; this.accessTokens = accessTokens; this.settings = settings; this.clock = clock;
        this.limiter = limiter; this.accountLimit = accountLimit;
        this.dummyHash = passwords.encode(TokenSecrets.generate());
    }

    /** Internal result only: the opaque secret is sent exclusively in the HttpOnly cookie. */
    public record SessionTokens(TokenResponse access, String refresh, Instant expiresAt) {
        @Override public String toString() { return "SessionTokens[redacted]"; }
    }

    @Transactional
    public SessionTokens login(LoginRequest request) {
        String email = users.normalizeEmail(request.email());
        limiter.requireAllowed("account:" + TokenSecrets.hash(email), accountLimit, Duration.ofMinutes(5));
        var credential = users.credentialForEmail(email).orElse(null);
        String hash = credential == null || credential.getPasswordHash() == null ? dummyHash : credential.getPasswordHash();
        boolean matches;
        try { matches = passwords.matches(request.password(), hash); }
        catch (IllegalArgumentException exception) { matches = false; }
        if (!matches || credential == null || credential.getPasswordHash() == null) throw new IdentityAuthenticationException();
        // No User entity was loaded before this lock: account/password changes cannot hide in the persistence cache.
        var user = users.lockById(credential.getId()).orElseThrow(IdentityAuthenticationException::new);
        if (!user.isActive() || !hash.equals(user.getPasswordHash())) throw new IdentityAuthenticationException();
        var session = sessions.saveAndFlush(new AuthSession(user.getId(), user.getAuthorizationVersion(), clock.instant().plus(settings.refreshTtl())));
        return issueRefresh(session);
    }

    @Transactional
    public SessionTokens createSession(User user) {
        var session = sessions.saveAndFlush(new AuthSession(user.getId(), user.getAuthorizationVersion(),
                clock.instant().plus(settings.refreshTtl())));
        return issueRefresh(session);
    }

    private SessionTokens issueRefresh(AuthSession session) {
        String raw = TokenSecrets.generate();
        refreshTokens.saveAndFlush(new RefreshToken(session.getId(), TokenSecrets.hash(raw), session.getExpiresAt()));
        return new SessionTokens(accessTokens.issue(session), raw, session.getExpiresAt());
    }

    @Transactional
    public SessionTokens refresh(String raw) {
        if (!TokenSecrets.valid(raw)) return null;
        String hash = TokenSecrets.hash(raw);
        var owner = refreshTokens.findOwner(hash).orElse(null);
        if (owner == null) return null;
        // Shared lock order for login/refresh/logout and future account-admin mutations: user, session, token.
        var user = users.lockById(owner.getUserId()).orElseThrow();
        var session = sessions.lockById(owner.getSessionId()).orElseThrow();
        var token = refreshTokens.findByTokenHash(hash).orElseThrow();
        var now = clock.instant();
        if (!usable(user, session, now) || token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now)) return null;
        if (token.getConsumedAt() != null) {
            session.revoke(now, "REFRESH_REUSE");
            sessions.saveAndFlush(session);
            // Return, don't throw: revocation MUST commit before the controller sends 401.
            return null;
        }
        token.consume(now);
        refreshTokens.saveAndFlush(token); // Release the partial unique index before inserting the successor.
        String successor = TokenSecrets.generate();
        var next = refreshTokens.saveAndFlush(new RefreshToken(session.getId(), TokenSecrets.hash(successor), session.getExpiresAt()));
        token.replacedBy(next.getId());
        refreshTokens.saveAndFlush(token);
        return new SessionTokens(accessTokens.issue(session), successor, session.getExpiresAt());
    }

    @Transactional
    public void logout(String raw) {
        if (!TokenSecrets.valid(raw)) return;
        var owner = refreshTokens.findOwner(TokenSecrets.hash(raw)).orElse(null);
        if (owner == null) return;
        users.lockById(owner.getUserId()).orElseThrow();
        var session = sessions.lockById(owner.getSessionId()).orElseThrow();
        session.revoke(clock.instant(), "LOGOUT");
        sessions.saveAndFlush(session);
    }

    private boolean usable(User user, AuthSession session, Instant now) {
        return user.isActive() && session.getUserId().equals(user.getId()) && session.getRevokedAt() == null
                && session.getExpiresAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).isAfter(now)
                && session.getAuthorizationVersion() == user.getAuthorizationVersion();
    }

    @Transactional(readOnly = true)
    public IdentityContext currentIdentity(UUID userId, UUID sessionId, long version) {
        var user = users.findById(userId).orElseThrow(IdentityAuthenticationException::new);
        var session = sessions.findById(sessionId).orElseThrow(IdentityAuthenticationException::new);
        if (!usable(user, session, clock.instant()) || version != user.getAuthorizationVersion()) throw new IdentityAuthenticationException();
        return new IdentityContext(userId, sessionId, roles.namesForUser(userId), version);
    }

    @Transactional(readOnly = true)
    public UserProfile profile(IdentityContext identity) {
        var current = currentIdentity(identity.userId(), identity.sessionId(), identity.authorizationVersion());
        var user = users.findById(current.userId()).orElseThrow(IdentityAuthenticationException::new);
        return new UserProfile(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), current.roles(),
                user.isActive(), user.getCreatedAt(), user.getUpdatedAt(), user.getVersion(),
                user.getPasswordHash() != null, oauthAccounts.existsByUserIdAndProvider(user.getId(), "google"));
    }
}
