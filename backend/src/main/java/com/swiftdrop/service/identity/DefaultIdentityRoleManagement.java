package com.swiftdrop.service.identity;

import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.dto.identity.RoleAssignmentResponse;
import com.swiftdrop.entity.identity.IdentityAuditEvent;
import com.swiftdrop.entity.identity.UserRole;
import com.swiftdrop.entity.identity.UserRoleId;
import com.swiftdrop.exception.IdentityRoleConflictException;
import com.swiftdrop.repository.identity.AuthSessionRepository;
import com.swiftdrop.repository.identity.IdentityAuditRepository;
import com.swiftdrop.repository.identity.RoleRepository;
import com.swiftdrop.repository.identity.UserRepository;
import com.swiftdrop.repository.identity.UserRoleRepository;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultIdentityRoleManagement implements IdentityRoleManagement {
    private final IdentityAccountService identities;
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final AuthSessionRepository sessions;
    private final IdentityAuditRepository audit;
    private final Clock clock;

    public DefaultIdentityRoleManagement(IdentityAccountService identities, UserRepository users,
            RoleRepository roles, UserRoleRepository userRoles, AuthSessionRepository sessions,
            IdentityAuditRepository audit, Clock clock) {
        this.identities = identities; this.users = users; this.roles = roles; this.userRoles = userRoles;
        this.sessions = sessions; this.audit = audit; this.clock = clock;
    }

    @Override @Transactional(readOnly = true)
    public RoleAssignmentResponse roleSummary(UUID targetUserId) {
        identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        if (targetUserId == null) throw new IdentityRoleConflictException();
        var user = users.findById(targetUserId).orElseThrow(IdentityRoleConflictException::new);
        return new RoleAssignmentResponse(targetUserId, roles.namesForUser(targetUserId), user.getVersion());
    }

    @Override @Transactional
    public void grantMerchantForApprovedOwner(UUID ownerUserId, String reason) {
        var actor = identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        grant(ownerUserId, IdentityRole.MERCHANT, actor.userId(), null, reason);
    }

    @Override @Transactional
    public void revokeMerchantForOwner(UUID ownerUserId, String reason) {
        var actor = identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        if (ownerUserId == null || reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("Invalid role change request");
        var user = users.lockById(ownerUserId).orElseThrow(IdentityRoleConflictException::new);
        var merchantRole = roles.findByName(IdentityRole.MERCHANT.name())
                .orElseThrow(() -> new IllegalStateException("Role definition missing"));
        Set<String> before = roles.namesForUser(ownerUserId);
        var association = new UserRoleId(ownerUserId, merchantRole.getId());
        if (userRoles.existsById(association)) userRoles.deleteById(association);
        userRoles.flush();
        user.authorizationChanged();
        users.saveAndFlush(user);
        var now = clock.instant();
        for (var session : sessions.findByUserIdAndRevokedAtIsNull(ownerUserId)) session.revoke(now, "MERCHANT_ACCESS_CHANGED");
        sessions.flush();
        Set<String> after = roles.namesForUser(ownerUserId);
        audit.saveAndFlush(IdentityAuditEvent.record("USER", actor.userId(), ownerUserId, "MERCHANT_ACCESS_REVOKED",
                reason.trim(), Map.of("roles", before), Map.of("roles", after)));
    }

    @Override @Transactional
    public RoleAssignmentResponse grantCourier(UUID targetUserId, long expectedVersion, String reason) {
        var actor = identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        return grant(targetUserId, IdentityRole.COURIER, actor.userId(), expectedVersion, reason);
    }

    private RoleAssignmentResponse grant(UUID targetUserId, IdentityRole role, UUID actorId,
            Long expectedVersion, String reason) {
        if (targetUserId == null || reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("Invalid role change request");
        var user = users.lockById(targetUserId).orElseThrow(IdentityRoleConflictException::new);
        if (!user.isActive() || !user.isEmailVerified()) throw new IdentityRoleConflictException();
        if (expectedVersion != null && user.getVersion() != expectedVersion) throw new IdentityRoleConflictException();
        var roleEntity = roles.findByName(role.name()).orElseThrow(() -> new IllegalStateException("Role definition missing"));
        var association = new UserRoleId(targetUserId, roleEntity.getId());
        if (userRoles.existsById(association)) throw new IdentityRoleConflictException();
        Set<String> before = roles.namesForUser(targetUserId);
        userRoles.saveAndFlush(new UserRole(targetUserId, roleEntity.getId()));
        user.authorizationChanged();
        users.saveAndFlush(user);
        var now = clock.instant();
        for (var session : sessions.findByUserIdAndRevokedAtIsNull(targetUserId)) session.revoke(now, "ROLE_CHANGED");
        sessions.flush();
        Set<String> after = roles.namesForUser(targetUserId);
        audit.saveAndFlush(IdentityAuditEvent.record("USER", actorId, targetUserId, "ROLE_GRANTED", reason.trim(),
                Map.of("roles", before), Map.of("roles", after)));
        return new RoleAssignmentResponse(targetUserId, after, user.getVersion());
    }
}
