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
        if (!user.isActive() || !user.isEmailVerified()) throw unavailable();
        var currentRoles = roles.namesForUser(userId);
        if (!currentRoles.contains(IdentityRole.COURIER.name())) throw unavailable();
        return new AccountSummary(user.getId(), user.getFullName(), true, currentRoles);
    }

    private static AccessDeniedException unavailable() { return new AccessDeniedException("Courier account is unavailable"); }
    @Override public java.util.Map<UUID, AccountLabel> accountLabels(java.util.Collection<UUID> ids) {
        requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        if (ids == null) throw new IllegalArgumentException("Account IDs required");
        if (ids.isEmpty()) return java.util.Map.of();
        return users.displayForIds(java.util.Set.copyOf(ids)).stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                UserRepository.AccountDisplay::getId, row -> new AccountLabel(row.getFullName(), row.getEmail())));
    }
    @Override public java.util.Set<UUID> eligibleCourierIds(java.util.Collection<UUID> candidateIds) {
        requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        if(candidateIds==null) throw new IllegalArgumentException("Courier candidates required");
        if(candidateIds.isEmpty()) return java.util.Set.of();
        return java.util.Set.copyOf(users.eligibleCourierIds(java.util.Set.copyOf(candidateIds)));
    }
    @Override public void requireActiveMerchant(UUID id) {
        if(!isActiveMerchantAccount(id))throw new AccessDeniedException("Merchant account unavailable");
    }
    @Override public boolean isActiveMerchantAccount(UUID id) {
        var user=users.findById(id).orElse(null);
        return user!=null&&user.isActive()&&user.isEmailVerified()&&roles.namesForUser(id).contains("MERCHANT");
    }
}
