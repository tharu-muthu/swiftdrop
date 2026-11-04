package com.swiftdrop.security;

import com.swiftdrop.exception.RateLimitException;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Single-backend bounded fixed windows. Keys contain source addresses or hashed account identifiers. */
@Component
public class AuthRateLimiter {
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();
    public AuthRateLimiter(Clock clock) { this.clock = clock; }
    public synchronized void requireAllowed(String key, int limit, Duration duration) {
        var now = clock.instant();
        windows.entrySet().removeIf(entry -> !entry.getValue().expiry.isAfter(now));
        var window = windows.get(key);
        if (window == null) {
            if (windows.size() >= 10_000) throw new RateLimitException(duration.toSeconds());
            window = new Window(now.plus(duration)); windows.put(key, window);
        }
        if (++window.count > limit) throw new RateLimitException(Math.max(1, Duration.between(now, window.expiry).toSeconds()));
    }
    private static class Window {
        final java.time.Instant expiry;
        int count;
        Window(java.time.Instant expiry) { this.expiry = expiry; }
    }
}
