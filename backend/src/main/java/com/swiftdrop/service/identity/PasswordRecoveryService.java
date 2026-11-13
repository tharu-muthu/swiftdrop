package com.swiftdrop.service.identity;

import com.swiftdrop.dto.identity.PasswordResetConfirmRequest;
import com.swiftdrop.dto.identity.PasswordResetRequest;
import com.swiftdrop.config.AuthSettings;
import com.swiftdrop.entity.identity.PasswordResetToken;
import com.swiftdrop.exception.InvalidResetTokenException;
import com.swiftdrop.integration.identity.PasswordResetDelivery;
import com.swiftdrop.repository.identity.AuthSessionRepository;
import com.swiftdrop.repository.identity.PasswordResetTokenRepository;
import com.swiftdrop.repository.identity.UserRepository;
import com.swiftdrop.security.AuthRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Service
public class PasswordRecoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private static final Duration TTL = Duration.ofMinutes(30);
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final PasswordResetDelivery delivery;
    private final TaskExecutor deliveryExecutor;
    private final AuthRateLimiter limiter;
    private final Clock clock;
    private final String frontendOrigin;

    public PasswordRecoveryService(UserRepository users, PasswordResetTokenRepository tokens,
            AuthSessionRepository sessions, PasswordEncoder passwords, PasswordResetDelivery delivery,
            @Qualifier("identityMailExecutor") TaskExecutor deliveryExecutor,
            AuthRateLimiter limiter, Clock clock, AuthSettings authSettings,
            @Value("${swiftdrop.recovery.frontend-origin}") String frontendOrigin) {
        this.users = users; this.tokens = tokens; this.sessions = sessions; this.passwords = passwords;
        this.delivery = delivery; this.deliveryExecutor = deliveryExecutor; this.limiter = limiter; this.clock = clock;
        URI origin = URI.create(frontendOrigin);
        if (origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null
                || origin.getFragment() != null || (origin.getPath() != null && !origin.getPath().isEmpty())
                || !("https".equals(origin.getScheme()) ||
                    (authSettings.localhostDevelopment() && "http".equals(origin.getScheme())
                            && List.of("localhost", "127.0.0.1").contains(origin.getHost())))) {
            throw new IllegalArgumentException("Configure a trusted HTTPS or loopback frontend origin for password recovery.");
        }
        this.frontendOrigin = origin.toString();
    }

    @Transactional
    public void request(PasswordResetRequest request) {
        String email = users.normalizeEmail(request.email());
        limiter.requireAllowed("recovery-account:" + TokenSecrets.hash(email), 3, Duration.ofMinutes(15));
        // Always perform token generation and hashing, including for unknown or ineligible accounts.
        String raw = TokenSecrets.generate();
        String hash = TokenSecrets.hash(raw);
        var candidate = users.findByEmailNormalized(email).orElse(null);
        if (candidate == null) return;
        var user = users.lockById(candidate.getId()).orElseThrow();
        if (!user.isActive() || user.getPasswordHash() == null) return;
        var now = clock.instant();
        for (var old : tokens.findByUserIdAndUsedAtIsNullAndRevokedAtIsNull(user.getId())) old.revoke(now);
        tokens.flush(); // Release the partial unique index before saving the replacement.
        tokens.saveAndFlush(new PasswordResetToken(user.getId(), hash, now.plus(TTL)));
        String link = frontendOrigin + "/reset-password#token=" + raw;
        String recipient = user.getEmail();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { deliveryExecutor.execute(() -> {
                    try { delivery.send(recipient, link); }
                    catch (RuntimeException exception) { LOG.warn("Password reset delivery failed; check mail transport configuration."); }
                }); }
                catch (RuntimeException exception) { LOG.warn("Password reset delivery failed; check mail transport configuration."); }
            }
        });
    }

    @Transactional
    public void confirm(PasswordResetConfirmRequest request) {
        if (!TokenSecrets.valid(request.token())) throw new InvalidResetTokenException();
        String hash = TokenSecrets.hash(request.token());
        var owner = tokens.ownerOf(hash).orElseThrow(InvalidResetTokenException::new);
        // Shared lock order with refresh/logout: user, session, then reset token.
        var user = users.lockById(owner).orElseThrow(InvalidResetTokenException::new);
        var token = tokens.findByTokenHash(hash).orElseThrow(InvalidResetTokenException::new);
        var now = clock.instant();
        if (!user.isActive() || user.getPasswordHash() == null || token.getUsedAt() != null
                || token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now)) throw new InvalidResetTokenException();
        user.resetPassword(passwords.encode(request.password()));
        user.verifyEmail(now); // A valid link also proves current ownership of the inbox.
        token.use(now);
        for (var other : tokens.findByUserIdAndUsedAtIsNullAndRevokedAtIsNull(user.getId())) {
            if (!other.getId().equals(token.getId())) other.revoke(now);
        }
        for (var session : sessions.findByUserIdAndRevokedAtIsNull(user.getId())) session.revoke(now, "PASSWORD_RESET");
        users.flush(); tokens.flush(); sessions.flush();
    }
}
