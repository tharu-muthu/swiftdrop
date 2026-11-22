package com.swiftdrop.integration.identity;

public interface AccountSecurityDelivery {
    void send(String recipient, String subject, String body);
}
