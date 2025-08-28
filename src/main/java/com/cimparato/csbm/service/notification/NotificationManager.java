package com.cimparato.csbm.service.notification;

import com.cimparato.csbm.aop.logging.LogMethod;
import com.cimparato.csbm.config.async.NotificationTaskExecutor;
import com.cimparato.csbm.domain.event.FileProcessingCompletedEvent;
import com.cimparato.csbm.dto.notification.NotificationDTO;
import com.cimparato.csbm.service.notification.factory.BaseNotification;
import com.cimparato.csbm.domain.notification.NotificationType;
import com.cimparato.csbm.service.notification.factory.NotificationFactory;
import com.cimparato.csbm.service.notification.rule.NotificationRule;
import com.cimparato.csbm.service.notification.handler.NotificationHandler;
import com.cimparato.csbm.service.notification.handler.NotificationHandlerStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.logging.LogLevel;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
public class NotificationManager {

    private final NotificationHandlerStrategy notificationHandlerStrategy;
    private final NotificationFactory notificationFactory;
    private final List<NotificationRule> notificationRules;
    private final NotificationTaskExecutor notificationExecutor;

    public NotificationManager(
            NotificationHandlerStrategy notificationHandlerStrategy,
            NotificationFactory notificationFactory,
            List<NotificationRule> notificationRules, NotificationTaskExecutor notificationExecutor
    ) {
        this.notificationHandlerStrategy = notificationHandlerStrategy;
        this.notificationFactory = notificationFactory;
        this.notificationRules = notificationRules;
        this.notificationExecutor = notificationExecutor;
    }

    /**
     * Gestisce l'evento di completamento dell'elaborazione di un file,
     * avviando l'elaborazione asincrona delle regole che verificano se è necessario salvare una nuova notifica.
     *
     */
    @EventListener
    public void notificationEventListener(FileProcessingCompletedEvent event) {
        String fileHash = event.getFileHash();
        String filename = event.getFilename();

        log.info("Scheduling notification processing for file: {} (fileHash: {})", filename, fileHash);

        try {

            notificationExecutor.executeWithFileHash(() -> {
                log.info("Starting notification processing for file: {} (hash: {})", filename, fileHash);
                processAllNotificationRules();
                log.info("Notification processing completed for file: {} (hash: {})", filename, fileHash);
            }, fileHash);

        } catch (Exception e) {
            handleSchedulingError(e, filename, fileHash);
        }
    }

    @LogMethod(level = LogLevel.INFO)
    public void processAllNotificationRules() {
        log.info("Processing {} notification rules", notificationRules.size());

        for (NotificationRule rule : notificationRules) {
            log.debug("Processing notification rule: {}", rule.getDescription());
            try {
                rule.checkAndQueueNotifications();
            } catch (Exception e) {
                log.error("Error processing notification rule {}: {}", rule.getDescription(), e.getMessage(), e);
            }
        }

        log.info("Completed processing all notification rules");
    }

    @LogMethod
    public void notifyUser(BaseNotification baseNotification, NotificationType channel) {
        NotificationHandler handler = notificationHandlerStrategy.getHandler(channel);
        handler.handle(baseNotification);
    }

    @LogMethod
    public void notifyUser(NotificationDTO notificationDTO) {
        var type = notificationDTO.getType();

        BaseNotification notification = notificationFactory.createNotification(type, notificationDTO);

        NotificationHandler handler = notificationHandlerStrategy.getHandler(type);

        handler.handle(notification);
    }

    private void handleSchedulingError(Exception e, String fileName, String fileHash) {
        if (e instanceof RejectedExecutionException) {
            // cattura l'eccezione di scheduling lanciata dalla policy di rifiuto del taskExecutor
            log.error("Failed to schedule notification for file {} (fileHash: {}) due to system overload: {}",
                    fileName, fileHash, e.getMessage());
        } else {
            log.error("Unexpected error while scheduling notification for file {} (fileHash: {}): {}",
                    fileName, fileHash, e.getMessage(), e);
        }
    }

}
