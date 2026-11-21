package com.swiftdrop.integration.notification;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Assignment mock adapter. Records success in the durable log; sends no external SMS. */
@Component
public class MockSmsProvider implements SmsProvider {
    public String send(UUID jobId,String phone,String message) {
        if(phone==null || phone.isBlank() || message==null || message.isBlank()) throw new IllegalArgumentException("INVALID_MESSAGE");
        return "mock-"+jobId;
    }
}
