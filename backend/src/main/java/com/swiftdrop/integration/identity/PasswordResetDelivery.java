package com.swiftdrop.integration.identity;

public interface PasswordResetDelivery {
    void send(String email, String link);
}
