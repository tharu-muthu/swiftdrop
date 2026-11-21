package com.swiftdrop.dto.webhook;

import java.time.Instant;
public record WebhookAttemptResponse(int attemptNumber,Instant startedAt,Instant completedAt,Integer httpStatus,String outcome,String errorMessage) { }
