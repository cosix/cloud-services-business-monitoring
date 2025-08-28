package com.cimparato.csbm.service.notification.factory;

import com.cimparato.csbm.domain.notification.NotificationType;
import com.cimparato.csbm.dto.notification.NotificationDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;


@Slf4j
@Component
public class NotificationFactory {

    private final ObjectMapper mapper;

    public NotificationFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Crea una notifica del tipo specificato con i parametri di base.
     *
     * @param type Tipo di notifica da creare
     * @return Una nuova istanza di BaseNotification del tipo specificato
     * @throws IllegalArgumentException se il tipo di notifica non è supportato
     */
    public BaseNotification createNotification(NotificationType type, NotificationDTO notification) {
        if (type == null) {
            throw new IllegalArgumentException("Notification type cannot be null");
        }

        return switch (type) {
            case EMAIL -> createEmailNotification(notification);
            case KAFKA -> createKafkaNotification(notification);
        };
    }

    private EmailNotification createEmailNotification(NotificationDTO notification) {
        return EmailNotification.builder()
                .type(NotificationType.EMAIL)
                .createdAt(LocalDateTime.now())
                .customerId(notification.getCustomerId())
                .recipient(notification.getRecipient())
                .sender(notification.getSender())
                .subject(notification.getSubject())
                .content(notification.getContent())
                .build();
    }

    private KafkaNotification createKafkaNotification(NotificationDTO notification) {
        String payload;
        try {
            payload = mapper.writeValueAsString(notification);
        } catch (JsonProcessingException e) {
            log.error("Failed to create a KafkaNotification", e);
            throw new IllegalArgumentException("Failed to create a KafkaNotification");
        }

        return KafkaNotification.builder()
                .type(NotificationType.KAFKA)
                .createdAt(LocalDateTime.now())
                .customerId(notification.getCustomerId())
                .topic(notification.getRecipient())
                .partitionKey(notification.getCustomerId())
                .payload(payload)
                .build();
    }

}
