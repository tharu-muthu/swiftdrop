package com.swiftdrop.security;

import com.swiftdrop.exception.RateLimitException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class AuthRateLimiterTest {
    @Test void windowsExpireWithoutPermanentlyLockingAnAccount() {
        var now = new AtomicReference<>(Instant.parse("2026-11-01T00:00:00Z"));
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        var limiter = new AuthRateLimiter(clock);
        limiter.requireAllowed("account:hashed-fixture", 1, Duration.ofMinutes(5));
        assertThatThrownBy(() -> limiter.requireAllowed("account:hashed-fixture", 1, Duration.ofMinutes(5)))
                .isInstanceOf(RateLimitException.class);
        assertThatCode(() -> limiter.requireAllowed("account:different-fixture", 1, Duration.ofMinutes(5))).doesNotThrowAnyException();
        now.set(now.get().plusSeconds(300));
        assertThatCode(() -> limiter.requireAllowed("account:hashed-fixture", 1, Duration.ofMinutes(5))).doesNotThrowAnyException();
    }
}
