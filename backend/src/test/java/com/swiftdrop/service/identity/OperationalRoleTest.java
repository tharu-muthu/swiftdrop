package com.swiftdrop.service.identity;

import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.entity.identity.*;
import com.swiftdrop.exception.IdentityRoleConflictException;
import com.swiftdrop.repository.identity.*;
import com.swiftdrop.security.IdentityContext;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationalRoleTest {
    final IdentityAccountService identities=mock(IdentityAccountService.class); final UserRepository users=mock(UserRepository.class); final RoleRepository roles=mock(RoleRepository.class);
    final UserRoleRepository memberships=mock(UserRoleRepository.class); final AuthSessionRepository sessions=mock(AuthSessionRepository.class); final IdentityAuditRepository audit=mock(IdentityAuditRepository.class);
    final DefaultIdentityRoleManagement service=new DefaultIdentityRoleManagement(identities,users,roles,memberships,sessions,audit,Clock.systemUTC());
    final UUID target=UUID.randomUUID(),actor=UUID.randomUUID(),roleId=UUID.randomUUID(); final User user=mock(User.class);
    void fixture() {
        when(identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN)).thenReturn(new IdentityContext(actor,UUID.randomUUID(),Set.of("DISPATCHER_ADMIN"),0));
        var role=mock(Role.class); when(role.getId()).thenReturn(roleId); when(roles.lockByName("DISPATCHER_ADMIN")).thenReturn(Optional.of(role)); when(roles.findByName("DISPATCHER_ADMIN")).thenReturn(Optional.of(role));
        when(users.lockById(target)).thenReturn(Optional.of(user)); when(user.isActive()).thenReturn(true); when(user.getVersion()).thenReturn(4L);
        when(memberships.existsById(new UserRoleId(target,roleId))).thenReturn(true);
    }
    @Test void refusesToRevokeTheLastActiveAdministrator() {
        fixture(); when(roles.countActiveAdministrators()).thenReturn(1L);
        assertThatThrownBy(()->service.revokeOperationalRole(target,IdentityRole.DISPATCHER_ADMIN,4,"Access review")).isInstanceOf(IdentityRoleConflictException.class);
        verify(memberships,never()).deleteById(any()); verifyNoInteractions(sessions,audit); verify(user,never()).authorizationChanged();
    }
    @Test void roleRevocationChecksRecordVersionBeforeWriting() {
        fixture(); when(roles.countActiveAdministrators()).thenReturn(2L);
        assertThatThrownBy(()->service.revokeOperationalRole(target,IdentityRole.DISPATCHER_ADMIN,3,"Access review")).isInstanceOf(IdentityRoleConflictException.class);
        verify(memberships,never()).deleteById(any()); verifyNoInteractions(sessions,audit);
    }
    @Test void successfulRevocationInvalidatesAuthorizationAndSessionsAndAudits() {
        fixture(); when(roles.countActiveAdministrators()).thenReturn(2L); when(roles.namesForUser(target)).thenReturn(Set.of("CUSTOMER","DISPATCHER_ADMIN")).thenReturn(Set.of("CUSTOMER"));
        var session=mock(AuthSession.class); when(sessions.findByUserIdAndRevokedAtIsNull(target)).thenReturn(List.of(session));
        var response=service.revokeOperationalRole(target,IdentityRole.DISPATCHER_ADMIN,4,"Access review");
        assertThat(response.roles()).containsExactly("CUSTOMER"); verify(user).authorizationChanged(); verify(session).revoke(any(),eq("ROLE_CHANGED")); verify(audit).saveAndFlush(any());
    }
    @Test void refusesSelfRemovalEvenWhenOtherAdministratorsExist() {
        fixture();
        when(identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN)).thenReturn(new IdentityContext(target, UUID.randomUUID(), Set.of("DISPATCHER_ADMIN"), 0));
        assertThatThrownBy(() -> service.revokeOperationalRole(target, IdentityRole.DISPATCHER_ADMIN, 4, "Self removal"))
                .isInstanceOf(IdentityRoleConflictException.class);
        verify(memberships, never()).deleteById(any());
    }
    @Test void refusesRemovingTheOnlyRole() {
        fixture(); when(roles.countActiveAdministrators()).thenReturn(2L);
        when(roles.namesForUser(target)).thenReturn(Set.of("DISPATCHER_ADMIN"));
        assertThatThrownBy(() -> service.revokeOperationalRole(target, IdentityRole.DISPATCHER_ADMIN, 4, "Access review"))
                .isInstanceOf(IdentityRoleConflictException.class);
        verify(memberships, never()).deleteById(any());
    }
    @Test void merchantCannotBeGrantedThroughTheAdminEditor() {
        fixture();
        assertThatThrownBy(() -> service.changeAdminManagedRole(target, IdentityRole.MERCHANT, true, 4, "Wrong workflow"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(memberships, sessions, audit);
    }
    @Test void unverifiedAccountsCannotReceiveStaffRoles() {
        fixture(); when(user.isEmailVerified()).thenReturn(false);
        assertThatThrownBy(() -> service.grantCourier(target, 4, "Courier onboarding"))
                .isInstanceOf(IdentityRoleConflictException.class);
        verify(memberships, never()).saveAndFlush(any()); verifyNoInteractions(sessions, audit);
    }
}
