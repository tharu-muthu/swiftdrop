package com.swiftdrop.service.identity;

import com.swiftdrop.dto.identity.ProfileUpdateRequest;
import com.swiftdrop.entity.identity.User;
import com.swiftdrop.exception.ProfileConflictException;
import com.swiftdrop.repository.identity.OAuthAccountRepository;
import com.swiftdrop.repository.identity.UserRepository;
import com.swiftdrop.security.IdentityContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final IdentitySessionService sessions = mock(IdentitySessionService.class);
    private final OAuthAccountRepository oauth = mock(OAuthAccountRepository.class);
    private final ProfileService service = new ProfileService(users, sessions, oauth);

    @Test void editsOnlyCurrentUserAndNormalizesPhone() {
        UUID id = UUID.randomUUID();
        var identity = new IdentityContext(id, UUID.randomUUID(), Set.of("CUSTOMER"), 0);
        User user = User.registerCustomer("Old Name", "person@example.test", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "version", 3L);
        when(sessions.currentIdentity(id, identity.sessionId(), 0)).thenReturn(identity);
        when(users.findById(id)).thenReturn(Optional.of(user));
        var result = service.update(identity, new ProfileUpdateRequest("  New Name  ", "+94 77-1234567", 3L));
        assertThat(result.fullName()).isEqualTo("New Name");
        assertThat(result.phone()).isEqualTo("+94771234567");
        assertThat(result.email()).isEqualTo("person@example.test");
        assertThat(result.roles()).containsExactly("CUSTOMER");
        verify(users).flush();
        verify(users).findById(id);
    }

    @Test void staleVersionDoesNotWrite() {
        UUID id = UUID.randomUUID();
        var identity = new IdentityContext(id, UUID.randomUUID(), Set.of("CUSTOMER"), 0);
        User user = User.registerCustomer("Old Name", "person@example.test", "hash");
        ReflectionTestUtils.setField(user, "version", 4L);
        when(sessions.currentIdentity(id, identity.sessionId(), 0)).thenReturn(identity);
        when(users.findById(id)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.update(identity, new ProfileUpdateRequest("New Name", null, 3L)))
                .isInstanceOf(ProfileConflictException.class);
        assertThat(user.getFullName()).isEqualTo("Old Name");
        verify(users, never()).flush();
    }
}
