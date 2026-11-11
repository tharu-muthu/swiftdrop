package com.swiftdrop.dto.webhook;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record WebhookSubscriptionUpdateRequest(
        @Size(max = 2048) String callbackUrl,
        @Size(max = 10) List<@Valid @Size(max = 100) String> eventTypes,
        Boolean active) { }
