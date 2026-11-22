package com.swiftdrop.service.identity;

import com.swiftdrop.config.AuthSettings;
import com.swiftdrop.dto.identity.AccountSecurityRequests.*;
import com.swiftdrop.entity.identity.*;
import com.swiftdrop.exception.*;
import com.swiftdrop.integration.identity.AccountSecurityDelivery;
import com.swiftdrop.repository.identity.*;
import com.swiftdrop.security.*;
import java.time.*;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

@Service
public class AccountSecurityService {
    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final IdentitySessionService identities;
    private final AccountActionTokenRepository tokens;
    private final PasswordResetTokenRepository resets;
    private final EmailVerificationTokenRepository verifications;
    private final OAuthAccountRepository links;
    private final IdentityAuditRepository audit;
    private final PasswordEncoder passwords;
    private final AccountSecurityDelivery mail;
    private final TaskExecutor executor;
    private final AuthRateLimiter limiter;
    private final Clock clock;
    private final String frontendOrigin;
    public AccountSecurityService(UserRepository users,AuthSessionRepository sessions,IdentitySessionService identities,
            AccountActionTokenRepository tokens,PasswordResetTokenRepository resets,EmailVerificationTokenRepository verifications,
            OAuthAccountRepository links,IdentityAuditRepository audit,PasswordEncoder passwords,AccountSecurityDelivery mail,
            @Qualifier("identityMailExecutor") TaskExecutor executor,AuthRateLimiter limiter,Clock clock,
            AuthSettings settings,@Value("${swiftdrop.recovery.frontend-origin}") String frontendOrigin) {
        this.users=users;this.sessions=sessions;this.identities=identities;this.tokens=tokens;this.resets=resets;
        this.verifications=verifications;this.links=links;this.audit=audit;this.passwords=passwords;this.mail=mail;
        this.executor=executor;this.limiter=limiter;this.clock=clock;
        var origin=java.net.URI.create(frontendOrigin);
        if(origin.getHost()==null||origin.getUserInfo()!=null||origin.getQuery()!=null||origin.getFragment()!=null||!origin.getPath().isEmpty()
                || !("https".equals(origin.getScheme()) || (settings.localhostDevelopment() && "http".equals(origin.getScheme())
                    && List.of("localhost","127.0.0.1").contains(origin.getHost()))))
            throw new IllegalArgumentException("Configure a trusted frontend origin for account security.");
        this.frontendOrigin=frontendOrigin;
    }
    private AccountSettingsException stepUp(){return new AccountSettingsException(403,"REAUTH_REQUIRED","Confirm your current password or verify your linked Google account again.");}
    private AccountSettingsException invalidLink(){return new AccountSettingsException(400,"INVALID_EMAIL_CHANGE","This email change link is invalid or expired. Request a new link from your profile.");}
    private User locked(IdentityContext identity) {
        var user=users.lockById(identity.userId()).orElseThrow(IdentityAuthenticationException::new);
        identities.currentIdentity(identity.userId(),identity.sessionId(),identity.authorizationVersion());
        return user;
    }
    private void authenticate(User user,IdentityContext identity,String password,String proof) {
        limiter.requireAllowed("account-stepup:"+user.getId(),5,Duration.ofMinutes(10));
        if(user.getPasswordHash()!=null) {
            if(password==null || !passwords.matches(password,user.getPasswordHash())) throw stepUp();
            return;
        }
        if(!TokenSecrets.valid(proof)) throw stepUp();
        var token=tokens.findByTokenHash(TokenSecrets.hash(proof)).orElseThrow(this::stepUp);
        if(!token.getUserId().equals(user.getId()) || !token.getSessionId().equals(identity.sessionId())
                || !token.getKind().equals("GOOGLE_REAUTH") || !token.usable(clock.instant())
                || token.getAuthorizationVersion()!=user.getAuthorizationVersion()) throw stepUp();
        token.use(clock.instant());
    }
    private String issue(User user,UUID sessionId,String kind,String email,Duration ttl) {
        var now=clock.instant();
        for(var token:tokens.findByUserIdAndUsedAtIsNullAndRevokedAtIsNull(user.getId()))
            if(token.getKind().equals(kind)) token.revoke(now);
        tokens.flush();
        String raw=TokenSecrets.generate();
        tokens.saveAndFlush(new AccountActionToken(user.getId(),sessionId,user.getAuthorizationVersion(),kind,TokenSecrets.hash(raw),email,now.plus(ttl)));
        return raw;
    }
    private void deliver(String recipient,String subject,String body) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){
                try{executor.execute(()->{try{mail.send(recipient,subject,body);}catch(RuntimeException failure){mailFailed();}});}
                catch(RuntimeException failure){mailFailed();}
            }
        });
    }
    private void mailFailed(){LoggerFactory.getLogger(AccountSecurityService.class).warn("Account security delivery failed; check SMTP configuration.");}
    private void invalidate(User user,String action) {
        var now=clock.instant();
        for(var session:sessions.findByUserIdAndRevokedAtIsNull(user.getId())) session.revoke(now,action);
        for(var token:tokens.findByUserIdAndUsedAtIsNullAndRevokedAtIsNull(user.getId())) token.revoke(now);
        for(var token:resets.findByUserIdAndUsedAtIsNullAndRevokedAtIsNull(user.getId())) token.revoke(now);
        for(var token:verifications.findByUserIdAndUsedAtIsNullAndRevokedAtIsNull(user.getId())) token.revoke(now);
        audit.save(IdentityAuditEvent.record("USER",user.getId(),user.getId(),action,"Account owner verified a security change",Map.of(),Map.of()));
    }
    @Transactional public void requestEmail(IdentityContext identity,ChangeEmail request) {
        var user=locked(identity);
        authenticate(user,identity,request.password(),request.reauthToken());
        limiter.requireAllowed("email-change:"+user.getId(),3,Duration.ofMinutes(15));
        String email=users.normalizeEmail(request.email());
        if(users.findByEmailNormalized(email).isPresent()) return; // Same generic response, including existing/current addresses.
        String raw=issue(user,identity.sessionId(),"EMAIL_CHANGE",email,Duration.ofMinutes(30));
        deliver(email,"Confirm your SwiftDrop email change","Confirm your new sign-in email. This link works once and expires in 30 minutes.\n\n"
            +frontendOrigin+"/change-email#token="+raw+"\n\nIf you did not request this change, ignore this email.");
    }
    @Transactional public void confirmEmail(String raw) {
        if(!TokenSecrets.valid(raw)) throw invalidLink();
        String hash=TokenSecrets.hash(raw);
        UUID owner=tokens.ownerOf(hash).orElseThrow(this::invalidLink);
        var user=users.lockById(owner).orElseThrow(this::invalidLink);
        var token=tokens.findByTokenHash(hash).orElseThrow(this::invalidLink);
        if(!user.isActive() || !user.isEmailVerified() || !token.getKind().equals("EMAIL_CHANGE")
                || !token.usable(clock.instant()) || token.getAuthorizationVersion()!=user.getAuthorizationVersion()) throw invalidLink();
        String previous=user.getEmail();
        user.changeEmail(token.getPendingEmail(),clock.instant());token.use(clock.instant());
        invalidate(user,"EMAIL_CHANGED");
        try { users.flush(); }
        catch(org.springframework.dao.DataIntegrityViolationException conflict) {
            throw new AccountSettingsException(409,"EMAIL_CHANGE_UNAVAILABLE","The email change could not be completed. Request a new link from your profile.");
        }
        deliver(previous,"Your SwiftDrop email was changed","Your SwiftDrop sign-in email has been changed and all sessions ended. If this was not you, contact your SwiftDrop administrator immediately.");
    }
    @Transactional public void changePassword(IdentityContext identity,ChangePassword request) {
        var user=locked(identity);authenticate(user,identity,request.currentPassword(),request.reauthToken());
        if(user.getPasswordHash()!=null && passwords.matches(request.password(),user.getPasswordHash()))
            throw new AccountSettingsException(400,"PASSWORD_UNCHANGED","Choose a different new password.");
        user.resetPassword(passwords.encode(request.password()));invalidate(user,"PASSWORD_CHANGED");users.flush();
        deliver(user.getEmail(),"Your SwiftDrop password was changed","Your SwiftDrop password was changed and all sessions ended. If this was not you, use password recovery and contact your administrator.");
    }
    public record GoogleIntent(UUID userId,UUID sessionId,long authorizationVersion,Instant expiresAt) implements java.io.Serializable { }
    @Transactional public GoogleIntent beginGoogle(IdentityContext identity) {
        var user=locked(identity);limiter.requireAllowed("google-reauth:"+user.getId(),5,Duration.ofMinutes(10));
        if(user.getPasswordHash()!=null || !links.existsByUserIdAndProvider(user.getId(),"google")) throw stepUp();
        return new GoogleIntent(user.getId(),identity.sessionId(),user.getAuthorizationVersion(),clock.instant().plusSeconds(300));
    }
    @Transactional public String completeGoogle(GoogleIntent intent,OidcUser google) {
        var now=clock.instant();
        if(intent==null || !intent.expiresAt().isAfter(now) || !Boolean.TRUE.equals(google.getEmailVerified())) throw stepUp();
        var authenticatedAt=google.getIdToken().getAuthenticatedAt();
        if(authenticatedAt==null || authenticatedAt.isBefore(now.minusSeconds(300)) || authenticatedAt.isAfter(now.plusSeconds(30))) throw stepUp();
        var user=locked(new IdentityContext(intent.userId(),intent.sessionId(),Set.of(),intent.authorizationVersion()));
        var link=links.findByProviderAndProviderSubject("google",google.getSubject()).orElseThrow(this::stepUp);
        if(!link.getUserId().equals(user.getId()) || user.getPasswordHash()!=null) throw stepUp();
        return issue(user,intent.sessionId(),"GOOGLE_REAUTH",null,Duration.ofMinutes(5));
    }
}
