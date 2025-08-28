package com.cimparato.csbm.service.notification.rule.impl;

import com.cimparato.csbm.aop.logging.LogMethod;
import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.config.properties.KafkaAppProperties;
import com.cimparato.csbm.dto.cloudservice.CustomerWithExpiredServicesDTO;
import com.cimparato.csbm.dto.cloudservice.ServiceWithExpirationDTO;
import com.cimparato.csbm.service.notification.factory.KafkaNotification;
import com.cimparato.csbm.dto.notification.NotificationDTO;
import com.cimparato.csbm.service.CloudServiceService;
import com.cimparato.csbm.domain.notification.NotificationType;
import com.cimparato.csbm.service.notification.rule.NotificationRule;
import com.cimparato.csbm.service.notification.handler.impl.KafkaNotificationHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.logging.LogLevel;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ExpiredServicesNotificationRule implements NotificationRule {

    private String notificationTopic;
    private String alertsCustomerExpiredTopic;
    private String sender;
    private String subject;
    private String content;
    private int maxExpiredServicesCount;

    private final AppProperties appProperties;
    private final KafkaAppProperties kafkaAppProperties;
    private final CloudServiceService cloudServiceService;
    private final KafkaNotificationHandler kafkaNotificationHandler;
    private final ObjectMapper mapper;

    public ExpiredServicesNotificationRule(
            AppProperties appProperties,
            KafkaAppProperties kafkaAppProperties,
            CloudServiceService cloudServiceService,
            KafkaNotificationHandler kafkaNotificationHandler,
            ObjectMapper mapper
    ) {
        this.appProperties = appProperties;
        this.kafkaAppProperties = kafkaAppProperties;
        this.kafkaNotificationHandler = kafkaNotificationHandler;
        this.cloudServiceService = cloudServiceService;
        this.mapper = mapper;
    }

    @PostConstruct
    public void init() {
        this.notificationTopic = kafkaAppProperties.getTopic().getNotification();
        this.alertsCustomerExpiredTopic = kafkaAppProperties.getTopic().getAlertCustomerExpired();

        var alert = appProperties.getNotification().getRule().getExpiredServicesNotificationRule().getAlert();
        this.sender = alert.getSender();
        this.subject = alert.getSubject();
        this.content = alert.getContent();

        this.maxExpiredServicesCount = appProperties.getNotification().getRule().getExpiredServicesNotificationRule().getMaxExpiredServicesCount();
    }

    @Override
    @LogMethod(level = LogLevel.DEBUG)
    public void checkAndQueueNotifications() {

        CustomerWithExpiredServicesDTO customersWithExpiredServices =
                cloudServiceService.getCustomersWithMaxExpiredServices(maxExpiredServicesCount);

        Map<String, Set<ServiceWithExpirationDTO>> customersMap = customersWithExpiredServices.map();

        if (customersMap == null || customersMap.isEmpty()) {
            log.info("Found 0 customers with more than {} expired services", maxExpiredServicesCount);
            return;
        }

        log.info("Found {} customers with more than {} expired services", customersMap.size(), maxExpiredServicesCount);

        customersMap.forEach(this::queueNotificationForCustomer);

    }

    private void queueNotificationForCustomer(String customerId, Set<ServiceWithExpirationDTO> expiredServices) {

        log.info("Queuing expired services notification for customer: {}", customerId);

        var sender = getSender();
        var subject = getSubject();
        var content = createContent(customerId, expiredServices, createServicesSummary(expiredServices));
        var notificationTopic = getNotificationTopic();
        var alertsCustomerExpiredTopic = getAlertsCustomerExpiredTopic();

        var data = NotificationDTO.builder()
                .type(NotificationType.KAFKA)
                .customerId(customerId)
                .sender(sender)
                .recipient(alertsCustomerExpiredTopic)
                .subject(subject)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            var payload = mapper.writeValueAsString(data);
            var notification = KafkaNotification.builder()
                    .topic(notificationTopic)
                    .partitionKey(customerId)
                    .payload(payload)
                    .build();

            kafkaNotificationHandler.handle(notification);

        } catch (Exception e) {

            if (e instanceof JsonProcessingException) {
                log.error("Failed to serialize message for notificationTopic: {}, key: {}", notificationTopic, customerId, e);
            } else {
                log.error("Failed to queue message for notificationTopic: {}, key: {}", notificationTopic, customerId, e);
            }

            // TODO: Implementare logica di fallback: salvare la notifica nel DB con stato "FAILED" per un tentativo successivo
        }

    }

    public String getNotificationTopic() {
        return notificationTopic;
    }

    public String getAlertsCustomerExpiredTopic() {
        return alertsCustomerExpiredTopic;
    }

    public String getSender() {
        return (sender != null && !sender.isEmpty() && !sender.isBlank()) ? sender : "";
    }

    public String getSubject() {
        return (subject != null && !subject.isEmpty() && !subject.isBlank()) ? subject : "";
    }

    public String getContent() {
        return (content != null && !content.isEmpty() && !content.isBlank()) ? content : "";
    }


    private static String createContent(String customerId, Set<ServiceWithExpirationDTO> expiredServices, String servicesSummary) {
        return String.format("Customer %s has %d expired services: %s",
                customerId, expiredServices.size(), servicesSummary);
    }

    private String createServicesSummary(Set<ServiceWithExpirationDTO> expiredServices) {
        if (expiredServices == null || expiredServices.isEmpty()) {
            return "No details available";
        }
        return expiredServices.stream()
                .map(item -> "service `" + item.serviceType() + "` expiration date: `" + item.expirationDate() + "`")
                .collect(Collectors.joining(", "));
    }

    @Override
    public String getDescription() {
        return "Notify customers with more than " + maxExpiredServicesCount + " expired services";
    }

}
