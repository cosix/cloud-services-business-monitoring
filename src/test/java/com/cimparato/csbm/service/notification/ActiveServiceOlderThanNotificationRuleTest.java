package com.cimparato.csbm.service.notification;

import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.config.properties.KafkaAppProperties;
import com.cimparato.csbm.domain.enumeration.CloudServiceStatus;
import com.cimparato.csbm.domain.enumeration.CloudServiceType;
import com.cimparato.csbm.domain.notification.NotificationType;
import com.cimparato.csbm.dto.cloudservice.CloudServiceDTO;
import com.cimparato.csbm.dto.notification.NotificationDTO;
import com.cimparato.csbm.service.CloudServiceService;
import com.cimparato.csbm.service.notification.factory.KafkaNotification;
import com.cimparato.csbm.service.notification.handler.impl.KafkaNotificationHandler;
import com.cimparato.csbm.service.notification.rule.impl.ActiveServiceOlderThanNotificationRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActiveServiceOlderThanNotificationRuleTest {

    @Mock
    private AppProperties appProperties;

    @Mock
    private KafkaAppProperties kafkaAppProperties;

    @Mock
    private CloudServiceService cloudServiceService;

    @Mock
    private KafkaNotificationHandler kafkaNotificationHandler;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private AppProperties.Rule rule;

    @Mock
    private AppProperties.ActiveServiceOlderThanNotificationRule activeServiceRule;

    @Mock
    private AppProperties.Email email;

    @Mock
    private AppProperties.Notification notification;

    @Mock
    private KafkaAppProperties.TopicConfig topicConfig;

    @Captor
    private ArgumentCaptor<KafkaNotification> notificationCaptor;

    @Captor
    private ArgumentCaptor<Object> payloadCaptor;

    private ActiveServiceOlderThanNotificationRule notificationRule;

    @BeforeEach
    void setUp() {
        when(appProperties.getNotification()).thenReturn(notification);
        when(notification.getRule()).thenReturn(rule);
        when(rule.getActiveServiceOlderThanNotificationRule()).thenReturn(activeServiceRule);
        when(activeServiceRule.getEmail()).thenReturn(email);
        when(activeServiceRule.getYears()).thenReturn(3);
        when(email.getSender()).thenReturn("system@example.com");
        when(email.getRecipient()).thenReturn("marketing@example.com");
        when(email.getSubject()).thenReturn("Long-term Active Service");
        when(email.getContent()).thenReturn("Customer has active service for more than 3 years");

        when(kafkaAppProperties.getTopic()).thenReturn(topicConfig);
        when(topicConfig.getNotification()).thenReturn("notifications");

        notificationRule = new ActiveServiceOlderThanNotificationRule(
                appProperties,
                kafkaAppProperties,
                cloudServiceService,
                kafkaNotificationHandler,
                mapper
        );

        notificationRule.init();
    }

    @Test
    @DisplayName("Verifica che la regola identifichi correttamente i servizi attivi da più di X anni")
    void testIdentifiesServicesOlderThanConfiguredYears() {

        // arrange
        List<CloudServiceDTO> oldServices = new ArrayList<>();
        CloudServiceDTO service1 = new CloudServiceDTO();
        service1.setCustomerId("CUST001");
        service1.setServiceType(CloudServiceType.PEC);
        service1.setActivationDate(LocalDate.of(2018, 1, 15));
        service1.setExpirationDate(LocalDate.of(2026, 11, 15));
        service1.setAmount(BigDecimal.valueOf(29.99));
        service1.setStatus(CloudServiceStatus.ACTIVE);

        CloudServiceDTO service2 = new CloudServiceDTO();
        service2.setCustomerId("CUST003");
        service2.setServiceType(CloudServiceType.FATTURAZIONE);
        service2.setActivationDate(LocalDate.of(2018, 2, 1));
        service2.setExpirationDate(LocalDate.of(2025, 11, 1));
        service2.setAmount(BigDecimal.valueOf(79.90));
        service2.setStatus(CloudServiceStatus.ACTIVE);

        oldServices.add(service1);
        oldServices.add(service2);

        when(cloudServiceService.getActiveServicesOlderThan(any(LocalDate.class))).thenReturn(oldServices);

        try {
            when(mapper.writeValueAsString(payloadCaptor.capture())).thenReturn("{\"test\":\"value\"}");
        } catch (JsonProcessingException e) {
            fail("JSON serialization failed", e);
        }

        // act
        notificationRule.checkAndQueueNotifications();

        // assert
        verify(cloudServiceService).getActiveServicesOlderThan(any(LocalDate.class));
        verify(kafkaNotificationHandler, times(2)).handle(notificationCaptor.capture());

        // verifica che siano state inviate il numero corretto di notifiche
        List<KafkaNotification> capturedNotifications = notificationCaptor.getAllValues();
        assertEquals(2, capturedNotifications.size());

        // verifica che le notifiche siano state inviate al topic corretto
        for (KafkaNotification notification : capturedNotifications) {
            assertEquals("notifications", notification.getTopic());
        }

        // verifica che i payload contengano i dati corretti
        List<Object> capturedPayloads = payloadCaptor.getAllValues();
        assertEquals(2, capturedPayloads.size());

        for (Object payload : capturedPayloads) {
            assertTrue(payload instanceof NotificationDTO);
            NotificationDTO dto = (NotificationDTO) payload;
            assertEquals("marketing@example.com", dto.getRecipient());
            assertTrue(dto.getCustomerId().equals("CUST001") || dto.getCustomerId().equals("CUST003"));
        }
    }

    @Test
    @DisplayName("Verifica che la regola non identifichi servizi attivi da meno di X anni")
    void testDoesNotIdentifyServicesLessThanConfiguredYears() {

        // arrange
        when(cloudServiceService.getActiveServicesOlderThan(any(LocalDate.class))).thenReturn(Collections.emptyList());

        // act
        notificationRule.checkAndQueueNotifications();

        // assert
        verify(cloudServiceService).getActiveServicesOlderThan(any(LocalDate.class));
        verify(kafkaNotificationHandler, never()).handle(any());
    }

    @Test
    @DisplayName("Verifica che la regola generi correttamente il contenuto della notifica con i dettagli del servizio")
    void testGeneratesCorrectNotificationContent() {

        // arrange
        CloudServiceDTO service = new CloudServiceDTO();
        service.setCustomerId("CUST001");
        service.setServiceType(CloudServiceType.PEC);
        service.setActivationDate(LocalDate.of(2018, 1, 15));
        service.setExpirationDate(LocalDate.of(2026, 11, 15));
        service.setAmount(BigDecimal.valueOf(29.99));
        service.setStatus(CloudServiceStatus.ACTIVE);

        when(cloudServiceService.getActiveServicesOlderThan(any(LocalDate.class))).thenReturn(List.of(service));

        try {
            when(mapper.writeValueAsString(payloadCaptor.capture())).thenReturn("{\"test\":\"value\"}");
        } catch (Exception e) {
            fail("Mock setup failed", e);
        }

        // act
        notificationRule.checkAndQueueNotifications();

        // assert
        verify(kafkaNotificationHandler).handle(notificationCaptor.capture());

        // verifica il contenuto della notifica
        Object capturedPayload = payloadCaptor.getValue();
        assertTrue(capturedPayload instanceof NotificationDTO);

        NotificationDTO dto = (NotificationDTO) capturedPayload;
        assertEquals("CUST001", dto.getCustomerId());
        assertEquals("marketing@example.com", dto.getRecipient());
        assertTrue(dto.getContent().contains("CUST001") ||
                dto.getContent().contains("Customer has active service for more than 3 years"));
    }

    @Test
    @DisplayName("Verifica che la regola invii correttamente una notifica di tipo EMAIL")
    void testSendsEmailNotification() {

        // arrange
        CloudServiceDTO service = new CloudServiceDTO();
        service.setCustomerId("CUST001");
        service.setServiceType(CloudServiceType.PEC);
        service.setActivationDate(LocalDate.of(2018, 1, 15));
        service.setExpirationDate(LocalDate.of(2026, 11, 15));
        service.setAmount(BigDecimal.valueOf(29.99));
        service.setStatus(CloudServiceStatus.ACTIVE);

        when(cloudServiceService.getActiveServicesOlderThan(any(LocalDate.class))).thenReturn(List.of(service));

        try {
            when(mapper.writeValueAsString(payloadCaptor.capture())).thenReturn("{\"test\":\"value\"}");
        } catch (JsonProcessingException e) {
            fail("JSON serialization failed", e);
        }

        // act
        notificationRule.checkAndQueueNotifications();

        // assert
        verify(kafkaNotificationHandler).handle(notificationCaptor.capture());

        // Verifica che la notifica sia stata inviata al topic corretto
        KafkaNotification capturedNotification = notificationCaptor.getValue();
        assertEquals("notifications", capturedNotification.getTopic());

        // Verifica che il payload contenga i dati corretti
        Object capturedPayload = payloadCaptor.getValue();
        assertTrue(capturedPayload instanceof NotificationDTO);

        NotificationDTO dto = (NotificationDTO) capturedPayload;
        assertEquals(NotificationType.EMAIL, dto.getType());
        assertEquals("marketing@example.com", dto.getRecipient());
    }

    @Test
    @DisplayName("Verifica che la regola gestisca correttamente gli errori di serializzazione")
    void testHandlesSerializationErrors() throws Exception {

        // arrange
        CloudServiceDTO service = new CloudServiceDTO();
        service.setCustomerId("CUST001");
        service.setServiceType(CloudServiceType.PEC);
        service.setActivationDate(LocalDate.of(2018, 1, 15));

        when(cloudServiceService.getActiveServicesOlderThan(any(LocalDate.class))).thenReturn(List.of(service));

        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Test serialization error") {});

        // act
        notificationRule.checkAndQueueNotifications();

        // assert
        verify(kafkaNotificationHandler, never()).handle(any());
    }

    @Test
    @DisplayName("Verifica che la regola gestisca correttamente gli errori di invio della notifica")
    void testHandlesNotificationSendingErrors() {

        // arrange
        CloudServiceDTO service = new CloudServiceDTO();
        service.setCustomerId("CUST001");
        service.setServiceType(CloudServiceType.PEC);
        service.setActivationDate(LocalDate.of(2018, 1, 15));

        when(cloudServiceService.getActiveServicesOlderThan(any(LocalDate.class))).thenReturn(List.of(service));

        try {
            when(mapper.writeValueAsString(any())).thenReturn("{\"test\":\"value\"}");
        } catch (JsonProcessingException e) {
            fail("JSON serialization failed", e);
        }

        // simula un errore durante l'invio della notifica
        doThrow(new RuntimeException("Test notification error")).when(kafkaNotificationHandler).handle(any());

        // act & assert
        assertDoesNotThrow(() -> notificationRule.checkAndQueueNotifications());
    }
}
