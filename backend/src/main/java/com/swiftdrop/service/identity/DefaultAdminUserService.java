package com.swiftdrop.service.identity;

import com.swiftdrop.dto.identity.AdminUserResponse;
import com.swiftdrop.dto.identity.AdminUsersPage;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.entity.identity.IdentityAuditEvent;
import com.swiftdrop.entity.identity.User;
import com.swiftdrop.exception.AdminUserConflictException;
import com.swiftdrop.exception.AdminUserNotFoundException;
import com.swiftdrop.repository.identity.AuthSessionRepository;
import com.swiftdrop.repository.identity.IdentityAuditRepository;
import com.swiftdrop.repository.identity.RoleRepository;
import com.swiftdrop.repository.identity.UserRepository;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultAdminUserService implements AdminUserService {
    private final IdentityAccountService identities;
    private final UserRepository users;
    private final RoleRepository roles;
    private final AuthSessionRepository sessions;
    private final IdentityAuditRepository audit;
    private final Clock clock;

    public DefaultAdminUserService(IdentityAccountService identities, UserRepository users,
            RoleRepository roles, AuthSessionRepository sessions, IdentityAuditRepository audit, Clock clock) {
        this.identities = identities; this.users = users; this.roles = roles;
        this.sessions = sessions; this.audit = audit; this.clock = clock;
    }

    @Override @Transactional(readOnly = true)
    public AdminUsersPage list(String query, String role, int page, int size) {
        identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page");
        String term = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
        if (term.length() > 200) throw new IllegalArgumentException("Search is too long");
        term = term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        String roleName = role == null ? "" : role.strip().toUpperCase(java.util.Locale.ROOT);
        if (!roleName.isEmpty() && !java.util.Set.of("CUSTOMER", "MERCHANT", "COURIER", "DISPATCHER_ADMIN").contains(roleName))
            throw new IllegalArgumentException("Invalid role filter");
        var result = users.searchForAdmin(term, roleName, PageRequest.of(page, size));
        return new AdminUsersPage(result.getContent().stream().map(this::safe).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override @Transactional(readOnly = true)
    public AdminUserResponse get(UUID id) {
        identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        return safe(users.findById(id).orElseThrow(AdminUserNotFoundException::new));
    }

    @Override @Transactional public AdminUserResponse suspend(UUID id, long expectedVersion, String reason) {
        return changeStatus(id, expectedVersion, reason, false);
    }

    @Override @Transactional public AdminUserResponse reactivate(UUID id, long expectedVersion, String reason) {
        return changeStatus(id, expectedVersion, reason, true);
    }

    private AdminUserResponse changeStatus(UUID id, long expectedVersion, String reason, boolean activate) {
        var actor = identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        if (id == null || expectedVersion < 0 || reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("Invalid status change");
        if (!activate && actor.userId().equals(id)) throw new AdminUserConflictException();
        // This shared role lock serializes concurrent attempts to suspend the last active administrator.
        roles.lockByName(IdentityRole.DISPATCHER_ADMIN.name())
                .orElseThrow(() -> new IllegalStateException("Administrator role missing"));
        var user = users.lockById(id).orElseThrow(AdminUserNotFoundException::new);
        if (user.getVersion() != expectedVersion || user.isActive() == activate) throw new AdminUserConflictException();
        var userRoles = roles.namesForUser(id);
        if (!activate && userRoles.contains(IdentityRole.DISPATCHER_ADMIN.name())
                && roles.countActiveAdministrators() <= 1) throw new AdminUserConflictException();
        boolean before = user.isActive();
        user.setActive(activate);
        users.saveAndFlush(user);
        var now = clock.instant();
        for (var session : sessions.findByUserIdAndRevokedAtIsNull(id)) session.revoke(now, "ACCOUNT_STATUS_CHANGED");
        sessions.flush();
        audit.saveAndFlush(IdentityAuditEvent.record("USER", actor.userId(), id,
                activate ? "ACCOUNT_REACTIVATED" : "ACCOUNT_SUSPENDED", reason.strip(),
                Map.of("active", before, "roles", userRoles), Map.of("active", activate, "roles", userRoles)));
        return safe(user);
    }

    private AdminUserResponse safe(User user) {
        return new AdminUserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                roles.namesForUser(user.getId()), user.isActive(), user.isEmailVerified(),
                user.getCreatedAt(), user.getUpdatedAt(), user.getVersion());
    }
}
