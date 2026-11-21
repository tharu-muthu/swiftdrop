package com.swiftdrop.integration.notification;

import java.util.UUID;

public interface SmsProvider {
    /** Implementations must deduplicate repeated calls with this stable job ID. */
    String send(UUID jobId,String phone,String message);
}
