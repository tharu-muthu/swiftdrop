package com.swiftdrop.integration.identity;

public interface EmailVerificationDelivery {
    void send(String email, String link);
}
