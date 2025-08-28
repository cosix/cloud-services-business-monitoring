package com.cimparato.csbm.service.notification.rule.impl;

import com.cimparato.csbm.aop.logging.LogMethod;
import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.config.properties.KafkaAppProperties;
import com.cimparato.csbm.dto.cloudservice.CloudServiceDTO;
import com.cimparato.csbm.service.notification.factory.KafkaNotification;
import com.cimparato.csbm.dto.notification.NotificationDTO;
import com.cimparato.csbm.service.CloudServiceService;
import com.cimparato.csbm.domain.notification.NotificationType;
import com.cimparato.csbm.service.notification.handler.impl.KafkaNotificationHandler;
import com.cimparato.csbm.service.notification.rule.NotificationRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.logging.LogLevel;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class ActiveServiceOlderThanNotificationRule implements NotificationRule {

    private String notificationTopic;
    private String sender;
    private String recipient;
    private String subject;
    private String content;
    private String description;
    private int years;

    private final AppProperties appProperties;
    private final KafkaAppProperties kafkaAppProperties;
    private final CloudServiceService cloudServiceService;
    private final KafkaNotificationHandler kafkaNotificationHandler;
    private final ObjectMapper mapper;

    public ActiveServiceOlderThanNotificationRule(
            AppProperties appProperties,
            KafkaAppProperties kafkaAppProperties,
            CloudServiceService cloudServiceService,
            KafkaNotificationHandler kafkaNotificationHandler,
            ObjectMapper mapper
    ) {
        this.appProperties = appProperties;
        this.kafkaAppProperties = kafkaAppProperties;
        this.cloudServiceService = cloudServiceService;
        this.kafkaNotificationHandler = kafkaNotificationHandler;
        this.mapper = mapper;
    }

    @PostConstruct
    public void init() {
        this.notificationTopic = kafkaAppProperties.getTopic().getNotification();

        var rule = appProperties.getNotification().getRule().getActiveServiceOlderThanNotificationRule();
        var email = rule.getEmail();

        this.sender = email.getSender();
        this.recipient = email.getRecipient();
        this.subject = email.getSubject();
        this.content = email.getContent();
        this.years = rule.getYears();
        this.description = "Notify marketing about services active for more than " + years + " years";
    }

    @Override
    @LogMethod(level = LogLevel.DEBUG)
    public void checkAndQueueNotifications() {
        LocalDate yearsAgo = LocalDate.now().minusYears(getYears());
        List<CloudServiceDTO> oldActiveServices = cloudServiceService.getActiveServicesOlderThan(yearsAgo);

        log.info("Found {} services active for more than {} years", oldActiveServices.size(), getYears());
        oldActiveServices.forEach(this::queueNotification);

    }

    private void queueNotification(CloudServiceDTO service) {

        log.info("Queuing notification for customer: {}, service: {}", service.getCustomerId(), service.getServiceType());

        var sender = getSender();
        var recipient = getRecipient();
        var subject = getSubject();
        var content = getContent(service);
        var customerId = service.getCustomerId();

        var data = NotificationDTO.builder()
                .type(NotificationType.EMAIL)
                .customerId(customerId)
                .sender(sender)
                .recipient(recipient)
                .subject(subject)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();


        var topic = getNotificationTopic();
        try {
            var payload = mapper.writeValueAsString(data);
            var notification = KafkaNotification.builder()
                    .topic(topic)
                    .partitionKey(customerId)
                    .payload(payload)
                    .build();

            kafkaNotificationHandler.handle(notification);

        } catch (Exception e) {

            if (e instanceof JsonProcessingException) {
                log.error("Failed to serialize message for topic: {}, key: {}", topic, customerId, e);
            } else {
                log.error("Failed to queue message for topic: {}, key: {}", topic, customerId, e);
            }

            // TODO: Implementare logica di fallback: salvare la notifica nel DB con stato "FAILED" per un tentativo successivo
        }

    }

    private String getContent(CloudServiceDTO service) {
        var content = getContent();
        return (content != null && !content.isEmpty() && !content.isBlank()) ? content : createContent(service);
    }

    private static String createContent(CloudServiceDTO service) {
        var content = "Customer `" + service.getCustomerId() + "` has service `" + service.getServiceType() +
                "` active since " + service.getActivationDate() + ". Consider contacting for upselling opportunities.";
        return content;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public String getNotificationTopic() {
        return notificationTopic;
    }

    public String getSender() {
        return sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getContent() {
        return content;
    }

    public int getYears() {
        return years;
    }
}
