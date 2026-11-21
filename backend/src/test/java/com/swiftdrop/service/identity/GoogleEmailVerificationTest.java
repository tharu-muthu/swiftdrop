package com.swiftdrop.service.identity;

import com.swiftdrop.entity.identity.Role;
import com.swiftdrop.entity.identity.User;
import com.swiftdrop.repository.identity.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleEmailVerificationTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserRoleRepository memberships = mock(UserRoleRepository.class);
    private final OAuthAccountRepository oauth = mock(OAuthAccountRepository.class);
    private final IdentitySessionService sessions = mock(IdentitySessionService.class);
    private final GoogleOAuthService service = new GoogleOAuthService(users, roles, memberships, oauth, sessions);

    @Test void verifiedProviderEmailMarksNewGoogleAccountVerified() {
        var oidc = mock(OidcUser.class);
        when(oidc.getEmailVerified()).thenReturn(true);
        when(oidc.getEmail()).thenReturn("person@example.test");
        when(oidc.getFullName()).thenReturn("Person");
        when(oidc.getSubject()).thenReturn("provider-subject");
        when(users.normalizeEmail("person@example.test")).thenReturn("person@example.test");
        when(users.findByEmailNormalized("person@example.test")).thenReturn(Optional.empty());
        UUID id = UUID.randomUUID();
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            ReflectionTestUtils.setField(user, "id", id);
            invokedUser = user;
            return user;
        });
        when(users.lockById(id)).thenAnswer(call -> Optional.of(invokedUser));
        var customer = mock(Role.class); when(customer.getId()).thenReturn(UUID.randomUUID());
        when(roles.findByName("CUSTOMER")).thenReturn(Optional.of(customer));
        var result = service.handleCallback(oidc);
        assertThat(result.outcome()).isEqualTo(GoogleOAuthService.Outcome.SUCCESS);
        verify(sessions).createSession(argThat(user -> user.isEmailVerified() && user.getPasswordHash() == null));
    }

    private User invokedUser;
}
