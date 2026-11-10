package com.swiftdrop.service.identity;

import com.swiftdrop.dto.identity.ProfileUpdateRequest;
import com.swiftdrop.dto.identity.UserProfile;
import com.swiftdrop.exception.IdentityAuthenticationException;
import com.swiftdrop.exception.ProfileConflictException;
import com.swiftdrop.repository.identity.UserRepository;
import com.swiftdrop.security.IdentityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserRepository users;
    private final IdentitySessionService sessions;
    private final com.swiftdrop.repository.identity.OAuthAccountRepository oauthAccounts;

    public ProfileService(UserRepository users, IdentitySessionService sessions,
                          com.swiftdrop.repository.identity.OAuthAccountRepository oauthAccounts) {
        this.users = users;
        this.sessions = sessions;
        this.oauthAccounts = oauthAccounts;
    }

    @Transactional
    public UserProfile update(IdentityContext identity, ProfileUpdateRequest request) {
        var current = sessions.currentIdentity(identity.userId(), identity.sessionId(), identity.authorizationVersion());
        var user = users.findById(current.userId()).orElseThrow(IdentityAuthenticationException::new);
        if (user.getVersion() != request.version()) throw new ProfileConflictException();
        String phone = request.phone();
        if (phone != null) {
            phone = phone.replace(" ", "").replace("-", "");
            if (phone.isEmpty()) phone = null;
        }
        user.updateProfile(request.fullName(), phone);
        users.flush(); // Detect concurrent @Version changes before reporting success.
        return new UserProfile(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), current.roles(),
                user.isActive(), user.getCreatedAt(), user.getUpdatedAt(), user.getVersion(),
                user.getPasswordHash() != null, oauthAccounts.existsByUserIdAndProvider(user.getId(), "google"));
    }
}
