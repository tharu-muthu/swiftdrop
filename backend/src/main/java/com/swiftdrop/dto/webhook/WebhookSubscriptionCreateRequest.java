package com.swiftdrop.dto.webhook;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record WebhookSubscriptionCreateRequest(
        @NotBlank @Size(max = 2048) String callbackUrl,
        @NotEmpty @Size(max = 10) List<@Valid @NotBlank @Size(max = 100) String> eventTypes) { }
