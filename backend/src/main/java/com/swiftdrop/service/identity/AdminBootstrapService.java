package com.swiftdrop.service.identity;

import com.swiftdrop.entity.identity.IdentityAuditEvent;
import com.swiftdrop.entity.identity.UserRole;
import com.swiftdrop.repository.identity.AuthSessionRepository;
import com.swiftdrop.repository.identity.IdentityAuditRepository;
import com.swiftdrop.repository.identity.RoleRepository;
import com.swiftdrop.repository.identity.UserRepository;
import com.swiftdrop.repository.identity.UserRoleRepository;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBootstrapService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final AuthSessionRepository sessions;
    private final IdentityAuditRepository audit;
    private final Clock clock;

    public AdminBootstrapService(UserRepository users, RoleRepository roles, UserRoleRepository userRoles,
            AuthSessionRepository sessions, IdentityAuditRepository audit, Clock clock) {
        this.users = users; this.roles = roles; this.userRoles = userRoles;
        this.sessions = sessions; this.audit = audit; this.clock = clock;
    }

    /** Only the explicitly enabled startup runner invokes this; no HTTP route exists. */
    @Transactional
    public void bootstrap(String email) {
        // Serialize competing first-start attempts on the seeded role row.
        var adminRole = roles.lockByName("DISPATCHER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Administrator role definition missing"));
        if (audit.existsByAction("BOOTSTRAP_ADMIN")) return;
        if (roles.countAdministrators() != 0) throw new IllegalStateException("Administrator already exists; bootstrap was not run");
        if (email == null || email.isBlank()) throw new IllegalStateException("Bootstrap administrator email is required");
        var normalized = users.normalizeEmail(email);
        var candidate = users.findByEmailNormalized(normalized)
                .orElseThrow(() -> new IllegalStateException("Bootstrap account must exist and be verified"));
        var user = users.lockById(candidate.getId()).orElseThrow();
        if (!user.isActive() || !user.isEmailVerified())
            throw new IllegalStateException("Bootstrap account must exist and be verified");
        Set<String> before = roles.namesForUser(user.getId());
        userRoles.saveAndFlush(new UserRole(user.getId(), adminRole.getId()));
        user.authorizationChanged();
        users.saveAndFlush(user);
        var now = clock.instant();
        for (var session : sessions.findByUserIdAndRevokedAtIsNull(user.getId())) session.revoke(now, "ADMIN_BOOTSTRAP");
        sessions.flush();
        Set<String> after = roles.namesForUser(user.getId());
        audit.saveAndFlush(IdentityAuditEvent.record("SYSTEM", null, user.getId(), "BOOTSTRAP_ADMIN",
                "Initial administrator bootstrap", Map.of("roles", before), Map.of("roles", after)));
    }
}
