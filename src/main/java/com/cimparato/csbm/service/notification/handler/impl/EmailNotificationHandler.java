package com.cimparato.csbm.service.notification.handler.impl;

import com.cimparato.csbm.service.notification.factory.EmailNotification;
import com.cimparato.csbm.service.notification.factory.BaseNotification;
import com.cimparato.csbm.domain.notification.NotificationType;
import com.cimparato.csbm.service.notification.handler.NotificationHandler;
import com.cimparato.csbm.web.rest.errors.NotificationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailNotificationHandler implements NotificationHandler {

    private final JavaMailSender emailSender;

    public EmailNotificationHandler(JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public void handle(BaseNotification baseNotification) throws NotificationException {
        try {

            var emailNotification = (EmailNotification) baseNotification;

            var recipient = emailNotification.getRecipient();

            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(recipient);
            mailMessage.setSubject(emailNotification.getSubject());
            mailMessage.setText(emailNotification.getContent());

            emailSender.send(mailMessage);

            log.info("Email message sent successfully for recipient: {} ", recipient);

        } catch (Exception e) {
            throw new NotificationException("Failed to send email notification", e);
        }
    }

    @Override
    public NotificationType supportedType() {
        return NotificationType.EMAIL;
    }
}
