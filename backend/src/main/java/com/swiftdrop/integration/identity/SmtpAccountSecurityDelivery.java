package com.swiftdrop.integration.identity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
@Component
public class SmtpAccountSecurityDelivery implements AccountSecurityDelivery {
    private final JavaMailSender sender; private final String from;
    public SmtpAccountSecurityDelivery(JavaMailSender sender,@Value("${swiftdrop.recovery.from}") String from){this.sender=sender;this.from=from;}
    public void send(String recipient,String subject,String body){
        var mail=new SimpleMailMessage();mail.setFrom(from);mail.setTo(recipient);mail.setSubject(subject);mail.setText(body);sender.send(mail);
    }
}
