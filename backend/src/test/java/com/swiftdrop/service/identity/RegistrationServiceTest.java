package com.swiftdrop.service.identity;

import com.swiftdrop.config.PasswordConfig;
import com.swiftdrop.dto.identity.RegistrationRequest;
import com.swiftdrop.entity.identity.*;
import com.swiftdrop.repository.identity.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationServiceTest {
    UserRepository users = mock(UserRepository.class);
    RoleRepository roles = mock(RoleRepository.class);
    UserRoleRepository memberships = mock(UserRoleRepository.class);
    PasswordEncoder passwords = mock(PasswordEncoder.class);
    RegistrationService service = new RegistrationService(users, roles, memberships, passwords);
    String password = "  a long passphrase 🔐  ";
    RegistrationRequest request = new RegistrationRequest("  Test User  ", " PERSON@example.test ", password);

    void configured() {
        var role = mock(Role.class);
        when(role.getId()).thenReturn(UUID.randomUUID());
        when(roles.findByName("CUSTOMER")).thenReturn(Optional.of(role));
        when(users.normalizeEmail(request.email())).thenReturn("person@example.test");
        when(passwords.encode(password)).thenReturn("encoded-only");
        when(users.saveAndFlush(any())).thenAnswer(call -> {
            User user = call.getArgument(0);
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
            return user;
        });
    }
    @Test
    void persistsOnlyCustomerWithExactPasswordAndSafeResponse() {
        configured();
        var response = service.register(request);
        assertThat(response.roles()).containsExactly("CUSTOMER");
        assertThat(response.fullName()).isEqualTo("Test User");
        assertThat(response.email()).isEqualTo("person@example.test");
        verify(passwords).encode(password);
        var captured = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getPasswordHash()).isEqualTo("encoded-only");
        assertThat(captured.getValue().isActive()).isTrue();
        verify(memberships).saveAndFlush(argThat(link -> link.getId().getUserId().equals(response.id())));
        assertThat(request.toString()).doesNotContain(password, request.email());
    }
    @Test
    void missingRoleStopsBeforeAccountPersistence() {
        when(roles.findByName("CUSTOMER")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.register(request)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(users, memberships, passwords);
    }
    @Test
    void membershipFailurePropagatesToTheTransactionBoundary() {
        configured();
        when(memberships.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("fixture"));
        assertThatThrownBy(() -> service.register(request)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test
    void adaptiveEncoderUsesSaltAndPreservesUnicodeSpacesAndLongPasswords() {
        var encoder = new PasswordConfig().passwordEncoder();
        String longPassword = " 🔐".repeat(40);
        String first = encoder.encode(longPassword);
        String second = encoder.encode(longPassword);
        assertThat(first).startsWith("{pbkdf2-sha256-600k}").isNotEqualTo(second).doesNotContain(longPassword);
        assertThat(encoder.matches(longPassword, first)).isTrue();
        assertThat(encoder.matches(longPassword.strip(), first)).isFalse();
    }
    @Test
    void beanValidationUsesCodePointsAndNeverTrimsPassword() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new RegistrationRequest("Person", "a@example.test", "abcd1234"))).isEmpty();
            assertThat(validator.validate(new RegistrationRequest("Person", "a@example.test", "🔐".repeat(8)))).isEmpty();
            assertThat(validator.validate(new RegistrationRequest("Person", "a@example.test", "🔐".repeat(128)))).isEmpty();
            for (String value : new String[] { null, "", "short", "🔐".repeat(7), "a".repeat(129) }) {
                String expected = value == null || value.isEmpty() ? "Enter a password."
                        : value.codePointCount(0, value.length()) < 8 ? "Password must contain at least 8 characters."
                        : "Password must contain no more than 128 characters.";
                assertThat(validator.validate(new RegistrationRequest("Person", "a@example.test", value)))
                        .singleElement().satisfies(v -> {
                            assertThat(v.getPropertyPath().toString()).isEqualTo("password");
                            assertThat(v.getMessage()).isEqualTo(expected);
                        });
            }
            assertThat(validator.validate(new RegistrationRequest("", "broken", password))).hasSize(2);
            assertThat(request.password()).isEqualTo(password);
        }
    }
}
