package com.swiftdrop.service.identity;

import com.swiftdrop.dto.identity.RegistrationRequest;
import com.swiftdrop.dto.identity.RegistrationResponse;
import com.swiftdrop.entity.identity.User;
import com.swiftdrop.entity.identity.UserRole;
import com.swiftdrop.repository.identity.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
public class RegistrationService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository memberships;
    private final PasswordEncoder passwords;

    public RegistrationService(UserRepository users, RoleRepository roles,
                               UserRoleRepository memberships, PasswordEncoder passwords) {
        this.users = users;
        this.roles = roles;
        this.memberships = memberships;
        this.passwords = passwords;
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        var customer = roles.findByName("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("Customer role is unavailable."));
        String email = users.normalizeEmail(request.email());
        var user = users.saveAndFlush(User.registerCustomer(request.fullName(), email, passwords.encode(request.password())));
        memberships.saveAndFlush(new UserRole(user.getId(), customer.getId()));
        // Transaction commits before the controller sends 201; the unique constraint arbitrates races.
        return new RegistrationResponse(user.getId(), user.getEmail(), user.getFullName(), Set.of("CUSTOMER"));
    }
}
