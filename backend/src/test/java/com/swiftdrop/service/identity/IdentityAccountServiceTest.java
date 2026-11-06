package com.swiftdrop.service.identity;

import com.swiftdrop.dto.identity.*;
import com.swiftdrop.entity.identity.User;
import com.swiftdrop.repository.identity.*;
import com.swiftdrop.security.IdentityContext;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityAccountServiceTest {
    final IdentitySessionService sessions = mock(IdentitySessionService.class);
    final UserRepository users = mock(UserRepository.class);
    final RoleRepository roles = mock(RoleRepository.class);
    final IdentityAccountService service = new DefaultIdentityAccountService(sessions, users, roles);
    final UUID actor = UUID.randomUUID(), sid = UUID.randomUUID(), target = UUID.randomUUID();

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void authenticate(Set<String> currentRoles) {
        var stale = new IdentityContext(actor, sid, Set.of("DISPATCHER_ADMIN"), 3);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(stale, null, List.of()));
        when(sessions.currentIdentity(actor, sid, 3)).thenReturn(new IdentityContext(actor, sid, currentRoles, 3));
    }
    @Test void missingAnonymousAndOtherPrincipalTypesFailClosed() {
        assertThatThrownBy(service::requireCurrentIdentity).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("fixture", "anonymousUser",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThatThrownBy(service::requireCurrentIdentity).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(sessions, users, roles);
    }
    @ParameterizedTest @EnumSource(IdentityRole.class)
    void eachRoleUsesCurrentDatabaseRoles(IdentityRole role) {
        authenticate(Set.of(role.name()));
        assertThat(service.requireCurrentRole(role).roles()).containsExactly(role.name());
    }
    @Test void staleAdminClaimDoesNotAllowTargetLookup() {
        authenticate(Set.of("CUSTOMER"));
        assertThatThrownBy(() -> service.requireActiveCourier(target)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(users, roles);
    }
    @Test void revocationFailureStopsLookup() {
        authenticate(Set.of("DISPATCHER_ADMIN"));
        when(sessions.currentIdentity(actor, sid, 3)).thenThrow(new com.swiftdrop.exception.IdentityAuthenticationException());
        assertThatThrownBy(() -> service.requireActiveCourier(target))
                .isInstanceOf(com.swiftdrop.exception.IdentityAuthenticationException.class);
        verifyNoInteractions(users, roles);
    }
    @Test void activeCourierReturnsOnlyAnImmutableSafeSummary() {
        authenticate(Set.of("DISPATCHER_ADMIN"));
        var user = mock(User.class);
        when(users.findById(target)).thenReturn(Optional.of(user));
        when(user.isActive()).thenReturn(true); when(user.getId()).thenReturn(target); when(user.getFullName()).thenReturn("Courier");
        var mutable = new HashSet<>(Set.of("CUSTOMER", "COURIER")); when(roles.namesForUser(target)).thenReturn(mutable);
        var summary = service.requireActiveCourier(target); mutable.clear();
        assertThat(summary.roles()).containsExactlyInAnyOrder("CUSTOMER", "COURIER");
        assertThatThrownBy(() -> summary.roles().clear()).isInstanceOf(UnsupportedOperationException.class);
        verify(user, never()).getPasswordHash(); verify(user, never()).getEmail(); verify(user, never()).getPhone();
    }
    @Test void missingInactiveAndWrongRoleShareTheSameFailure() {
        authenticate(Set.of("DISPATCHER_ADMIN"));
        when(users.findById(target)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireActiveCourier(target)).hasMessage("Courier account is unavailable");
        var user = mock(User.class); when(users.findById(target)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.requireActiveCourier(target)).hasMessage("Courier account is unavailable");
        when(user.isActive()).thenReturn(true); when(roles.namesForUser(target)).thenReturn(Set.of("CUSTOMER"));
        assertThatThrownBy(() -> service.requireActiveCourier(target)).hasMessage("Courier account is unavailable");
    }
}
