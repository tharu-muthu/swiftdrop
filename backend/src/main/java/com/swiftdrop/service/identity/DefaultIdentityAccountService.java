package com.swiftdrop.service.identity;

import com.swiftdrop.dto.identity.*;
import com.swiftdrop.repository.identity.RoleRepository;
import com.swiftdrop.repository.identity.UserRepository;
import com.swiftdrop.security.IdentityContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultIdentityAccountService implements IdentityAccountService {
    private final IdentitySessionService sessions;
    private final UserRepository users;
    private final RoleRepository roles;

    public DefaultIdentityAccountService(IdentitySessionService sessions, UserRepository users, RoleRepository roles) {
        this.sessions = sessions; this.users = users; this.roles = roles;
    }

    @Override public IdentityContext requireCurrentIdentity() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IdentityContext identity)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        // Revalidate current account/session/version and reload roles, rather than trusting a supplied DTO or stale authorities.
        return sessions.currentIdentity(identity.userId(), identity.sessionId(), identity.authorizationVersion());
    }

    @Override public IdentityContext requireCurrentRole(IdentityRole role) {
        var identity = requireCurrentIdentity();
        if (!identity.roles().contains(Objects.requireNonNull(role).name())) throw new AccessDeniedException("Access denied");
        return identity;
    }

    @Override public AccountSummary requireActiveCourier(UUID userId) {
        // Authorize BEFORE looking up targets; customers cannot enumerate accounts.
        requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        if (userId == null) throw unavailable();
        var user = users.findById(userId).orElseThrow(DefaultIdentityAccountService::unavailable);
        if (!user.isActive()) throw unavailable();
        var currentRoles = roles.namesForUser(userId);
        if (!currentRoles.contains(IdentityRole.COURIER.name())) throw unavailable();
        return new AccountSummary(user.getId(), user.getFullName(), true, currentRoles);
    }

    private static AccessDeniedException unavailable() { return new AccessDeniedException("Courier account is unavailable"); }
}
