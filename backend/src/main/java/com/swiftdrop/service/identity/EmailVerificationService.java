package com.swiftdrop.service.identity;

import com.swiftdrop.config.AuthSettings;
import com.swiftdrop.dto.identity.EmailVerificationConfirmRequest;
import com.swiftdrop.dto.identity.EmailVerificationRequest;
import com.swiftdrop.entity.identity.EmailVerificationToken;
import com.swiftdrop.entity.identity.User;
import com.swiftdrop.exception.InvalidVerificationTokenException;
import com.swiftdrop.integration.identity.EmailVerificationDelivery;
import com.swiftdrop.repository.identity.AuthSessionRepository;
import com.swiftdrop.repository.identity.EmailVerificationTokenRepository;
import com.swiftdrop.repository.identity.UserRepository;
import com.swiftdrop.security.AuthRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Service
public class EmailVerificationService {
    private static final Logger LOG = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final Duration TTL = Duration.ofHours(24);
    private final UserRepository users;
    private final EmailVerificationTokenRepository tokens;
    private final AuthSessionRepository sessions;
    private final EmailVerificationDelivery delivery;
    private final TaskExecutor executor;
    private final AuthRateLimiter limiter;
    private final Clock clock;
    private final String frontendOrigin;

    public EmailVerificationService(UserRepository users, EmailVerificationTokenRepository tokens,
            AuthSessionRepository sessions, EmailVerificationDelivery delivery,
            @Qualifier("identityMailExecutor") TaskExecutor executor, AuthRateLimiter limiter,
            Clock clock, AuthSettings settings, @Value("${swiftdrop.recovery.frontend-origin}") String frontendOrigin) {
        this.users = users; this.tokens = tokens; this.sessions = sessions; this.delivery = delivery;
        this.executor = executor; this.limiter = limiter; this.clock = clock;
        URI origin = URI.create(frontendOrigin);
        if (origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null
                || origin.getFragment() != null || (origin.getPath() != null && !origin.getPath().isEmpty())
                || !("https".equals(origin.getScheme()) || (settings.localhostDevelopment()
                    && "http".equals(origin.getScheme()) && List.of("localhost", "127.0.0.1").contains(origin.getHost())))) {
            throw new IllegalArgumentException("Configure a trusted frontend origin for email verification.");
        }
        this.frontendOrigin = origin.toString();
    }

    /** Called inside the registration transaction after account and CUSTOMER role are persisted. */
    public void issueForRegistration(User user) { issue(user); }

    @Transactional
    public void request(EmailVerificationRequest request) {
        String email = users.normalizeEmail(request.email());
        limiter.requireAllowed("verify-account:" + TokenSecrets.hash(email), 3, Duration.ofMinutes(15));
        TokenSecrets.hash(TokenSecrets.generate()); // Comparable work for unknown addresses.
        var candidate = users.findByEmailNormalized(email).orElse(null);
        if (candidate == null) return;
        var user = users.lockById(candidate.getId()).orElseThrow();
        if (!user.isActive() || user.isEmailVerified() || user.getPasswordHash() == null) return;
        issue(user);
    }

    private void issue(User user) {
        var now = clock.instant();
        for (var old : tokens.findByUserIdAndUsedAtIsNullAndRevokedAtIsNull(user.getId())) old.revoke(now);
        tokens.flush();
        String raw = TokenSecrets.generate();
        tokens.saveAndFlush(new EmailVerificationToken(user.getId(), TokenSecrets.hash(raw), now.plus(TTL)));
        String link = frontendOrigin + "/verify-email#token=" + raw;
        String recipient = user.getEmail();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { executor.execute(() -> {
                    try { delivery.send(recipient, link); }
                    catch (RuntimeException exception) { LOG.warn("Email verification delivery failed; check mail transport configuration."); }
                }); }
                catch (RuntimeException exception) { LOG.warn("Email verification delivery failed; check mail transport configuration."); }
            }
        });
    }

    @Transactional
    public void confirm(EmailVerificationConfirmRequest request) {
        if (!TokenSecrets.valid(request.token())) throw new InvalidVerificationTokenException();
        String hash = TokenSecrets.hash(request.token());
        var owner = tokens.ownerOf(hash).orElseThrow(InvalidVerificationTokenException::new);
        var user = users.lockById(owner).orElseThrow(InvalidVerificationTokenException::new);
        var token = tokens.findByTokenHash(hash).orElseThrow(InvalidVerificationTokenException::new);
        var now = clock.instant();
        if (!user.isActive() || user.isEmailVerified() || token.getUsedAt() != null
                || token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now)) throw new InvalidVerificationTokenException();
        user.verifyEmail(now);
        token.use(now);
        for (var other : tokens.findByUserIdAndUsedAtIsNullAndRevokedAtIsNull(user.getId())) {
            if (!other.getId().equals(token.getId())) other.revoke(now);
        }
        for (var session : sessions.findByUserIdAndRevokedAtIsNull(user.getId())) session.revoke(now, "EMAIL_VERIFIED");
        users.flush(); tokens.flush(); sessions.flush();
    }
}
