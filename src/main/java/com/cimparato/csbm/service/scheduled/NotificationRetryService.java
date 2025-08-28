package com.cimparato.csbm.service.scheduled;

import com.cimparato.csbm.aop.logging.LogMethod;
import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.config.properties.KafkaAppProperties;
import com.cimparato.csbm.domain.model.Notification;
import com.cimparato.csbm.domain.notification.NotificationType;
import com.cimparato.csbm.service.notification.NotificationManager;
import com.cimparato.csbm.service.notification.NotificationService;
import com.cimparato.csbm.service.notification.factory.BaseNotification;
import com.cimparato.csbm.service.notification.factory.EmailNotification;
import com.cimparato.csbm.service.notification.factory.KafkaNotification;
import com.cimparato.csbm.domain.notification.NotificationStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.logging.LogLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class NotificationRetryService {

    private final NotificationService notificationService;
    private final NotificationManager notificationManager;
    private final ObjectMapper mapper;
    private final AppProperties appProperties;
    private final KafkaAppProperties kafkaAppProperties;

    public NotificationRetryService(NotificationService notificationService, NotificationManager notificationManager, ObjectMapper mapper, AppProperties appProperties, KafkaAppProperties kafkaAppProperties) {
        this.notificationService = notificationService;
        this.notificationManager = notificationManager;
        this.mapper = mapper;
        this.appProperties = appProperties;
        this.kafkaAppProperties = kafkaAppProperties;
    }

    /**
     * Recupera le notifica in stato FAILED che non abbiano raggiunto il numero massimo di retry, per ognuna di esse:
     * - aggiorna lo stato in PROCESSING
     * - costruisce il messaggio da inviare
     * - invoca il NotificationManager per inviare la notifica
     * - se la notifica risulta inviata, imposta lo stato a SENT
     * - in caso di errore imposta lo stato in FAILED aggiornando il retry count e il messaggio di errore
     */
    @Deprecated
    @LogMethod(level = LogLevel.DEBUG, measureTime = true)
//    @Scheduled(cron = "${app.async.task-scheduler.job-scheduling.failed-notifications-retry-cron-cron:0 */15 * * * *}") // default 15 minuti
    @Transactional
    public void retryNotificationsJob() {

        List<Notification> failedNotifications = notificationService.findFailed();

        log.info("Found {} notifications with status {}", failedNotifications.size(), NotificationStatus.FAILED.name());

        var i = 0;
        var notificationsSize = failedNotifications.size();
        for (Notification notification : failedNotifications) {
            try {

                var notificationId = notification.getId();

                i = i + 1;
                log.info("Sending notification {}/{} - notification id `{}`, channel `{}`",
                        i, notificationsSize, notificationId, notification.getType());

                notification.setStatus(NotificationStatus.PROCESSING);
                notificationService.save(notification);

                BaseNotification baseNotification = buildMessageFromNotification(notification);
                notificationManager.notifyUser(baseNotification, notification.getType());

                notification.setStatus(NotificationStatus.SENT);
                notification.setSentAt(LocalDateTime.now());

                log.info("Notification with id `{}` sent successfully", notificationId);

            } catch (Exception e) {
                log.warn("Error on sending notification with id `{}` : ", notification.getId(), e.getMessage());

                notification.setStatus(NotificationStatus.FAILED);
                notification.setErrorMessage(e.getMessage());
                notification.setRetryCount(notification.getRetryCount() + 1);
            }

            notificationService.save(notification);
        }
    }

    private BaseNotification buildMessageFromNotification(Notification notification) {
        NotificationType type = notification.getType();

        BaseNotification baseNotification = null;

        if (NotificationType.EMAIL.equals(type)) {

            baseNotification = EmailNotification.builder()
                    .type(type)
                    .recipient(notification.getRecipient())
                    .subject(notification.getSubject())
                    .content(notification.getContent())
                    .build();

        } else if (NotificationType.KAFKA.equals(type)) {

            var key = notification.getCustomerId();
            var topic = kafkaAppProperties.getTopic().getAlertCustomerExpired();
            try {
                var payload = mapper.writeValueAsString(notification);
                baseNotification = KafkaNotification.builder()
                        .type(type)
                        .topic(topic)
                        .partitionKey(key)
                        .payload(payload)
                        .build();

            } catch (JsonProcessingException e) {
                log.error("Failed to serialize message for topic: {}, key: {}", topic, key, e);
            }

        } else {
            throw new IllegalArgumentException("Invalid notification type: " + type.name());
        }

        return baseNotification;
    }
}
