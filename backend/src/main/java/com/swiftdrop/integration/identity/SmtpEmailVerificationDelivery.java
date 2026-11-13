package com.swiftdrop.integration.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailVerificationDelivery implements EmailVerificationDelivery {
    private final JavaMailSender sender;
    private final String from;
    public SmtpEmailVerificationDelivery(JavaMailSender sender, @Value("${swiftdrop.recovery.from}") String from) {
        this.sender = sender; this.from = from;
    }
    @Override public void send(String email, String link) {
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email);
        message.setSubject("Verify your SwiftDrop email");
        message.setText("Confirm that this email belongs to your SwiftDrop account. This link works once and expires in 24 hours.\n\n"
                + link + "\n\nIf you did not create this account, you can ignore this message.");
        sender.send(message);
    }
}
