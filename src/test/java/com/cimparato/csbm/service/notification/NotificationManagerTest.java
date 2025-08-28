package com.cimparato.csbm.service.notification;

import com.cimparato.csbm.config.async.NotificationTaskExecutor;
import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.domain.event.FileProcessingCompletedEvent;
import com.cimparato.csbm.domain.notification.NotificationType;
import com.cimparato.csbm.dto.fileupload.FileUploadJobDTO;
import com.cimparato.csbm.dto.notification.NotificationDTO;
import com.cimparato.csbm.service.notification.factory.EmailNotification;
import com.cimparato.csbm.service.notification.factory.KafkaNotification;
import com.cimparato.csbm.service.notification.factory.NotificationFactory;
import com.cimparato.csbm.service.notification.handler.NotificationHandler;
import com.cimparato.csbm.service.notification.handler.NotificationHandlerStrategy;
import com.cimparato.csbm.service.notification.rule.NotificationRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationManagerTest {

    @Mock
    private NotificationHandlerStrategy notificationHandlerStrategy;

    @Mock
    private NotificationFactory notificationFactory;

    @Mock
    private List<NotificationRule> notificationRules;

    @Mock
    private NotificationTaskExecutor notificationExecutor;

    @Mock
    private NotificationHandler emailHandler;

    @Mock
    private NotificationHandler kafkaHandler;

    @Mock
    private EmailNotification emailNotification;

    @Mock
    private KafkaNotification kafkaNotification;

    private NotificationManager notificationManager;

    @BeforeEach
    void setUp() {
        notificationManager = new NotificationManager(
                notificationHandlerStrategy,
                notificationFactory,
                notificationRules,
                notificationExecutor
        );
    }

    @Test
    @DisplayName("Verifica che il NotificationManager instanzi l'handler corretto in base al tipo di notifica")
    void testRoutesToCorrectHandlerBasedOnNotificationType() {

        when(notificationHandlerStrategy.getHandler(NotificationType.EMAIL)).thenReturn(emailHandler);
        when(notificationHandlerStrategy.getHandler(NotificationType.KAFKA)).thenReturn(kafkaHandler);

        // arrange
        NotificationDTO emailNotificationDTO = NotificationDTO.builder()
                .type(NotificationType.EMAIL)
                .customerId("CUST001")
                .content("Test email content")
                .build();

        NotificationDTO kafkaNotificationDTO = NotificationDTO.builder()
                .type(NotificationType.KAFKA)
                .customerId("CUST001")
                .content("Test kafka content")
                .build();

        when(notificationFactory.createNotification(NotificationType.EMAIL, emailNotificationDTO))
                .thenReturn(emailNotification);
        when(notificationFactory.createNotification(NotificationType.KAFKA, kafkaNotificationDTO))
                .thenReturn(kafkaNotification);

        // act
        notificationManager.notifyUser(emailNotificationDTO);
        notificationManager.notifyUser(kafkaNotificationDTO);

        // assert
        verify(notificationHandlerStrategy).getHandler(NotificationType.EMAIL);
        verify(notificationHandlerStrategy).getHandler(NotificationType.KAFKA);
        verify(emailHandler).handle(emailNotification);
        verify(kafkaHandler).handle(kafkaNotification);
    }

    @Test
    @DisplayName("Verifica che l'evento FileProcessingCompletedEvent attivi l'elaborazione asincrona delle notifiche")
    void testFileProcessingCompletedEventTriggersAsyncNotificationProcessing() {

        when(notificationRules.size()).thenReturn(0);
        when(notificationRules.iterator()).thenReturn(Collections.emptyIterator());

        // configura il comportamento del notificationExecutor
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(notificationExecutor).executeWithFileHash(any(Runnable.class), anyString());

        // arrange
        FileUploadJobDTO jobDTO = FileUploadJobDTO.builder()
                .fileHash("abc123")
                .filename("test.csv")
                .jobId("job-123")
                .jobStatus(JobStatus.COMPLETED)
                .build();

        FileProcessingCompletedEvent event = new FileProcessingCompletedEvent(jobDTO);

        // act
        notificationManager.notificationEventListener(event);

        // assert
        verify(notificationExecutor).executeWithFileHash(any(Runnable.class), eq("abc123"));
        verify(notificationRules).size();
    }
}
