package com.swiftdrop.service.identity;

import com.swiftdrop.entity.identity.*;
import com.swiftdrop.exception.*;
import com.swiftdrop.repository.identity.*;
import com.swiftdrop.security.IdentityContext;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleLinkServiceTest {
    final IdentityAccountService identities=mock(IdentityAccountService.class); final IdentitySessionService sessions=mock(IdentitySessionService.class);
    final UserRepository users=mock(UserRepository.class); final OAuthAccountRepository links=mock(OAuthAccountRepository.class); final IdentityAuditRepository audit=mock(IdentityAuditRepository.class);
    final PasswordEncoder passwords=mock(PasswordEncoder.class); final Instant now=Instant.parse("2026-11-21T10:00:00Z");
    final GoogleLinkService service=new GoogleLinkService(identities,sessions,users,links,audit,passwords,Clock.fixed(now,ZoneOffset.UTC));
    final UUID userId=UUID.randomUUID(),sessionId=UUID.randomUUID();
    final GoogleLinkService.Intent intent=new GoogleLinkService.Intent(userId,sessionId,3,now.plusSeconds(300));
    @Test void beginsOnlyAfterCurrentAccountPasswordProof() {
        when(identities.requireCurrentIdentity()).thenReturn(new IdentityContext(userId,sessionId,Set.of("CUSTOMER"),3));
        var user=mock(User.class); when(user.getId()).thenReturn(userId); when(user.getPasswordHash()).thenReturn("hash"); when(users.findById(userId)).thenReturn(Optional.of(user));
        assertThatThrownBy(()->service.begin("wrong")).isInstanceOf(IdentityAuthenticationException.class);
        when(passwords.matches("correct","hash")).thenReturn(true); assertThat(service.begin("correct")).isEqualTo(intent);
    }
    @Test void consentTargetsTheAuthenticatedUserWithoutEmailMatching() {
        var user=mock(User.class); when(user.getId()).thenReturn(userId); when(users.lockById(userId)).thenReturn(Optional.of(user));
        var google=mock(OidcUser.class); when(google.getEmailVerified()).thenReturn(true); when(google.getSubject()).thenReturn("verified-subject");
        service.complete(intent,google); verify(sessions).currentIdentity(userId,sessionId,3);
        verify(links).saveAndFlush(argThat(link->userId.equals(link.getUserId())&&"verified-subject".equals(link.getProviderSubject())));
        verify(users,never()).findByEmailNormalized(any()); verify(google,never()).getEmail(); verify(audit).save(any());
    }
    @Test void expiredConsentCannotWriteALink() {
        assertThatThrownBy(()->service.complete(new GoogleLinkService.Intent(userId,sessionId,3,now.minusSeconds(1)),mock(OidcUser.class))).isInstanceOf(IdentityAuthenticationException.class);
        verifyNoInteractions(users,links,audit);
    }
    @Test void revokedSessionCannotWriteALink() {
        var google=mock(OidcUser.class); when(google.getEmailVerified()).thenReturn(true); when(google.getSubject()).thenReturn("subject"); when(users.lockById(userId)).thenReturn(Optional.of(mock(User.class)));
        when(sessions.currentIdentity(userId,sessionId,3)).thenThrow(new IdentityAuthenticationException());
        assertThatThrownBy(()->service.complete(intent,google)).isInstanceOf(IdentityAuthenticationException.class); verifyNoInteractions(links,audit);
    }
}
