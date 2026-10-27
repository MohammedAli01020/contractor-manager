package com.contractor.app.common.email;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PlatformEmailService {
    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;
    private final String username;

    public PlatformEmailService(JavaMailSender mailSender,
            @Value("${app.email.enabled:false}") boolean enabled,
            @Value("${app.email.from:}") String from,
            @Value("${spring.mail.username:}") String username) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
        this.username = username;
    }

    public boolean isEnabled() { return enabled; }

    public void sendActivation(String to, String fullName, String activationUrl, LocalDateTime expiresAt) {
        if (!enabled || username == null || username.trim().isEmpty() || from == null || from.trim().isEmpty()) {
            throw new IllegalStateException("Email delivery is not configured.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("تفعيل حساب مدير الشركة - Contractor Manager");
        message.setText("مرحبًا " + fullName + "،\n\n"
                + "لإعداد كلمة المرور وتفعيل حسابك، افتح الرابط التالي:\n" + activationUrl + "\n\n"
                + "ينتهي الرابط في: " + expiresAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " UTC\n\n"
                + "إذا لم تطلب إنشاء الحساب، يمكنك تجاهل هذه الرسالة.");
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            // Never log the message or its activation URL/token.
            throw new IllegalStateException("Email delivery failed.", ex);
        }
    }
}
