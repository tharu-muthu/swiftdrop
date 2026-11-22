package com.swiftdrop.service.identity;

import com.swiftdrop.entity.identity.*;
import com.swiftdrop.exception.IdentityAuthenticationException;
import com.swiftdrop.exception.IdentityRoleConflictException;
import com.swiftdrop.repository.identity.*;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleLinkService {
    private final IdentityAccountService identities; private final IdentitySessionService sessions;
    private final UserRepository users; private final OAuthAccountRepository links; private final IdentityAuditRepository audit;
    private final PasswordEncoder passwords; private final Clock clock;
    public GoogleLinkService(IdentityAccountService identities,IdentitySessionService sessions,UserRepository users,OAuthAccountRepository links,
            IdentityAuditRepository audit,PasswordEncoder passwords,Clock clock) {
        this.identities=identities; this.sessions=sessions; this.users=users; this.links=links; this.audit=audit; this.passwords=passwords; this.clock=clock;
    }
    public record Intent(UUID userId,UUID sessionId,long authorizationVersion,Instant expiresAt) implements java.io.Serializable { }
    @Transactional(readOnly=true) public Intent begin(String password) {
        var identity=identities.requireCurrentIdentity();
        var user=users.findById(identity.userId()).orElseThrow(IdentityAuthenticationException::new);
        if(user.getPasswordHash()==null||!passwords.matches(password,user.getPasswordHash())) throw new IdentityAuthenticationException();
        if(links.existsByUserIdAndProvider(user.getId(),"google")) throw new IdentityRoleConflictException();
        return new Intent(identity.userId(),identity.sessionId(),identity.authorizationVersion(),clock.instant().plusSeconds(300));
    }
    @Transactional public void complete(Intent intent,OidcUser google) {
        if(intent==null||!intent.expiresAt().isAfter(clock.instant())||!Boolean.TRUE.equals(google.getEmailVerified())||google.getSubject()==null||google.getSubject().isBlank()) throw new IdentityAuthenticationException();
        var user=users.lockById(intent.userId()).orElseThrow(IdentityAuthenticationException::new);
        sessions.currentIdentity(intent.userId(),intent.sessionId(),intent.authorizationVersion());
        if(links.existsByUserIdAndProvider(intent.userId(),"google")||links.findByProviderAndProviderSubject("google",google.getSubject()).isPresent()) throw new IdentityRoleConflictException();
        // Consent is bound to the authenticated account and provider subject. Email never selects the target.
        links.saveAndFlush(new OAuthAccount(user.getId(),"google",google.getSubject()));
        audit.save(IdentityAuditEvent.record("USER",user.getId(),user.getId(),"GOOGLE_ACCOUNT_LINKED","Explicit link after password reauthentication",Map.of(),Map.of("provider","google")));
    }
}
