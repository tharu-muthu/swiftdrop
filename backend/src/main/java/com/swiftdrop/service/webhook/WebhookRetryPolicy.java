package com.swiftdrop.service.webhook;

import java.time.Duration;

/** Deterministic retry classification. Persistence and scheduling remain M9 adapter concerns. */
public final class WebhookRetryPolicy {
    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maximumDelay;

    public WebhookRetryPolicy(int maxAttempts, Duration initialDelay, Duration maximumDelay) {
        if (maxAttempts < 1 || initialDelay.isNegative() || initialDelay.isZero()
                || maximumDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("Invalid webhook retry policy.");
        }
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.maximumDelay = maximumDelay;
    }

    public Decision networkFailure(int attempt) {
        return decide(attempt, true);
    }

    public Decision response(int attempt, int status) {
        boolean retryable = status == 408 || status == 429 || status >= 500;
        return decide(attempt, retryable);
    }

    private Decision decide(int attempt, boolean retryable) {
        if (attempt < 1) throw new IllegalArgumentException("Attempt must be positive.");
        if (!retryable) return new Decision(false, Duration.ZERO, "FAILED");
        if (attempt >= maxAttempts) return new Decision(false, Duration.ZERO, "FAILED");
        long multiplier = 1L << Math.min(attempt - 1, 30);
        Duration delay = initialDelay.multipliedBy(multiplier);
        if (delay.compareTo(maximumDelay) > 0) delay = maximumDelay;
        return new Decision(true, delay, "RETRYING");
    }

    public record Decision(boolean retry, Duration delay, String state) { }
}
