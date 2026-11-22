package com.swiftdrop.service.identity;

import com.swiftdrop.config.AuthSettings;
import com.swiftdrop.dto.identity.PasswordResetRequest;
import com.swiftdrop.exception.RateLimitException;
import com.swiftdrop.integration.identity.PasswordResetDelivery;
import com.swiftdrop.repository.identity.AuthSessionRepository;
import com.swiftdrop.repository.identity.PasswordResetTokenRepository;
import com.swiftdrop.repository.identity.UserRepository;
import com.swiftdrop.security.AuthRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Clock;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordRecoveryServiceTest {
    @Test void unknownAddressIsGenericButPerAccountRequestsAreBounded() {
        var users = mock(UserRepository.class);
        var tokens = mock(PasswordResetTokenRepository.class);
        var sessions = mock(AuthSessionRepository.class);
        var delivery = mock(PasswordResetDelivery.class);
        var auth = mock(AuthSettings.class);
        when(auth.localhostDevelopment()).thenReturn(true);
        when(users.normalizeEmail("unknown@example.test")).thenReturn("unknown@example.test");
        when(users.findByEmailNormalized("unknown@example.test")).thenReturn(Optional.empty());
        var service = new PasswordRecoveryService(users, tokens, sessions, mock(PasswordEncoder.class), delivery,
                mock(TaskExecutor.class), new AuthRateLimiter(Clock.systemUTC()), Clock.systemUTC(), auth,
                "http://localhost:8080");
        var request = new PasswordResetRequest("unknown@example.test");
        for (int attempt = 0; attempt < 3; attempt++) service.request(request);
        assertThatThrownBy(() -> service.request(request)).isInstanceOf(RateLimitException.class);
        verifyNoInteractions(delivery, tokens, sessions);
    }
}
