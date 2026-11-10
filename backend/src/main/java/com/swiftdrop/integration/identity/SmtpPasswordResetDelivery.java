package com.swiftdrop.integration.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpPasswordResetDelivery implements PasswordResetDelivery {
    private final JavaMailSender sender;
    private final String from;
    public SmtpPasswordResetDelivery(JavaMailSender sender, @Value("${swiftdrop.recovery.from}") String from) {
        this.sender = sender; this.from = from;
    }
    @Override public void send(String email, String link) {
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email);
        message.setSubject("Reset your SwiftDrop password");
        message.setText("Use this link to reset your password. It expires in 30 minutes and works once.\n\n"
                + link + "\n\nIf you did not request this, you can ignore this message.");
        sender.send(message);
    }
}
