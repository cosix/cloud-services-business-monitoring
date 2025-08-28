package com.cimparato.csbm.messaging;

import com.cimparato.csbm.domain.notification.NotificationType;
import com.cimparato.csbm.dto.notification.NotificationDTO;
import com.cimparato.csbm.service.MessageDeduplicationService;
import com.cimparato.csbm.service.notification.NotificationManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerTest {

    @Mock
    private ObjectMapper mapper;

    @Mock
    private NotificationManager notificationManager;

    @Mock
    private MessageDeduplicationService messageDeduplicationService;

    private KafkaConsumer kafkaConsumer;

    @BeforeEach
    void setUp() {
        kafkaConsumer = new KafkaConsumer(mapper, notificationManager, messageDeduplicationService);
    }

    @Test
    @DisplayName("Verifica che il consumer riceva correttamente i messaggi dal topic configurato")
    void testReceivesMessagesFromConfiguredTopic() throws Exception {

        // arrange
        String message = "{\"type\":\"EMAIL\",\"customerId\":\"CUST001\",\"content\":\"Test content\"}";
        NotificationDTO notificationDTO = createTestNotification();
        String messageId = "test-message-id";

        when(mapper.readValue(anyString(), eq(NotificationDTO.class))).thenReturn(notificationDTO);
        when(messageDeduplicationService.generateMessageId(any(NotificationDTO.class))).thenReturn(messageId);
        when(messageDeduplicationService.isProcessed(messageId)).thenReturn(false);

        // act
        kafkaConsumer.consumeNotification(message, "notifications", 0, 123L);

        // assert
        verify(mapper).readValue(message, NotificationDTO.class);
        verify(messageDeduplicationService).generateMessageId(notificationDTO);
        verify(messageDeduplicationService).isProcessed(messageId);
        verify(notificationManager).notifyUser(notificationDTO);
        verify(messageDeduplicationService).markAsProcessed(messageId);
    }

    @Test
    @DisplayName("Verifica che il consumer deserializzi correttamente i messaggi e li passi al NotificationManager")
    void testDeserializesMessagesAndPassesToNotificationManager() throws Exception {

        // arrange
        String emailMessage = "{\"type\":\"EMAIL\",\"customerId\":\"CUST001\",\"content\":\"Test content\"}";
        NotificationDTO emailNotification = createTestNotification();
        emailNotification.setType(NotificationType.EMAIL);

        String kafkaMessage = "{\"type\":\"KAFKA\",\"customerId\":\"CUST001\",\"content\":\"Test content\"}";
        NotificationDTO kafkaNotification = createTestNotification();
        kafkaNotification.setType(NotificationType.KAFKA);

        when(mapper.readValue(eq(emailMessage), eq(NotificationDTO.class))).thenReturn(emailNotification);
        when(mapper.readValue(eq(kafkaMessage), eq(NotificationDTO.class))).thenReturn(kafkaNotification);
        when(messageDeduplicationService.generateMessageId(any(NotificationDTO.class))).thenReturn("id1", "id2");
        when(messageDeduplicationService.isProcessed(anyString())).thenReturn(false);

        // act
        kafkaConsumer.consumeNotification(emailMessage, "notifications", 0, 123L);
        kafkaConsumer.consumeNotification(kafkaMessage, "notifications", 0, 124L);

        // assert
        verify(notificationManager).notifyUser(emailNotification);
        verify(notificationManager).notifyUser(kafkaNotification);
    }

    @Test
    @DisplayName("Verifica che il consumer gestisca correttamente gli errori di deserializzazione")
    void testHandlesDeserializationErrors() throws Exception {

        // arrange
        String invalidMessage = "{invalid-json}";

        when(mapper.readValue(anyString(), eq(NotificationDTO.class)))
                .thenThrow(new JsonProcessingException("Invalid JSON") {});

        // act & assert
        assertThrows(RuntimeException.class, () ->
                kafkaConsumer.consumeNotification(invalidMessage, "notifications", 0, 123L));

        verify(notificationManager, never()).notifyUser(any(NotificationDTO.class));
    }

    @Test
    @DisplayName("Verifica che il consumer implementi correttamente il meccanismo di deduplicazione")
    void testImplementsDeduplicationMechanism() throws Exception {

        // arrange
        String message = "{\"type\":\"EMAIL\",\"customerId\":\"CUST001\",\"content\":\"Test content\"}";
        NotificationDTO notificationDTO = createTestNotification();
        String messageId = "duplicate-message-id";

        when(mapper.readValue(anyString(), eq(NotificationDTO.class))).thenReturn(notificationDTO);
        when(messageDeduplicationService.generateMessageId(any(NotificationDTO.class))).thenReturn(messageId);
        when(messageDeduplicationService.isProcessed(messageId)).thenReturn(true); // Messaggio già processato

        // act
        kafkaConsumer.consumeNotification(message, "notifications", 0, 123L);

        // assert
        verify(messageDeduplicationService).isProcessed(messageId);
        verify(notificationManager, never()).notifyUser(any(NotificationDTO.class));
        verify(messageDeduplicationService, never()).markAsProcessed(anyString());
    }

    @Test
    @DisplayName("Verifica che il consumer gestisca correttamente i messaggi con escape")
    void testHandlesEscapedMessages() throws Exception {

        // arrange
        String escapedMessage = "\"\\\"type\\\":\\\"EMAIL\\\",\\\"customerId\\\":\\\"CUST001\\\",\\\"content\\\":\\\"Test content\\\"\"";
        String unescapedMessage = "{\"type\":\"EMAIL\",\"customerId\":\"CUST001\",\"content\":\"Test content\"}";
        NotificationDTO notificationDTO = createTestNotification();
        String messageId = "test-message-id";

        when(mapper.readValue(eq(escapedMessage), eq(String.class))).thenReturn(unescapedMessage);
        when(mapper.readValue(eq(unescapedMessage), eq(NotificationDTO.class))).thenReturn(notificationDTO);
        when(messageDeduplicationService.generateMessageId(any(NotificationDTO.class))).thenReturn(messageId);
        when(messageDeduplicationService.isProcessed(messageId)).thenReturn(false);

        // act
        kafkaConsumer.consumeNotification(escapedMessage, "notifications", 0, 123L);

        // assert
        verify(mapper).readValue(escapedMessage, String.class);
        verify(mapper).readValue(unescapedMessage, NotificationDTO.class);
        verify(notificationManager).notifyUser(notificationDTO);
    }

    private NotificationDTO createTestNotification() {
        return NotificationDTO.builder()
                .type(NotificationType.EMAIL)
                .customerId("CUST001")
                .sender("system@example.com")
                .recipient("customer@example.com")
                .subject("Test Subject")
                .content("Test content")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
