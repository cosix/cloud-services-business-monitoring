package com.cimparato.csbm.service.notification;

import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.config.properties.KafkaAppProperties;
import com.cimparato.csbm.domain.enumeration.CloudServiceType;
import com.cimparato.csbm.domain.notification.NotificationType;
import com.cimparato.csbm.dto.cloudservice.CustomerWithExpiredServicesDTO;
import com.cimparato.csbm.dto.cloudservice.ServiceWithExpirationDTO;
import com.cimparato.csbm.dto.notification.NotificationDTO;
import com.cimparato.csbm.messaging.KafkaProducer;
import com.cimparato.csbm.service.CloudServiceService;
import com.cimparato.csbm.service.notification.factory.KafkaNotification;
import com.cimparato.csbm.service.notification.handler.impl.KafkaNotificationHandler;
import com.cimparato.csbm.service.notification.rule.impl.ExpiredServicesNotificationRule;
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
import org.springframework.kafka.support.SendResult;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerWithExpiredServicesNotificationRuleTest {

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
    private AppProperties.ExpiredServicesNotificationRule expiredServicesRule;

    @Mock
    private AppProperties.Alert alert;

    @Mock
    private AppProperties.Notification notification;

    @Mock
    private KafkaAppProperties.TopicConfig topicConfig;

    @Captor
    private ArgumentCaptor<KafkaNotification> notificationCaptor;

    private ExpiredServicesNotificationRule notificationRule;

    @BeforeEach
    void setUp() {
        when(appProperties.getNotification()).thenReturn(notification);
        when(notification.getRule()).thenReturn(rule);
        when(rule.getExpiredServicesNotificationRule()).thenReturn(expiredServicesRule);
        when(expiredServicesRule.getAlert()).thenReturn(alert);
        when(expiredServicesRule.getMaxExpiredServicesCount()).thenReturn(5);
        when(alert.getSender()).thenReturn("system@example.com");
        when(alert.getSubject()).thenReturn("Expired Services Alert");
        when(alert.getContent()).thenReturn("Customer has expired services");

        when(kafkaAppProperties.getTopic()).thenReturn(topicConfig);
        when(topicConfig.getNotification()).thenReturn("notifications");
        when(topicConfig.getAlertCustomerExpired()).thenReturn("alerts.customer_expired");

        notificationRule = new ExpiredServicesNotificationRule(
                appProperties,
                kafkaAppProperties,
                cloudServiceService,
                kafkaNotificationHandler,
                mapper
        );

        notificationRule.init();
    }

    @Test
    @DisplayName("Verifica che la regola identifichi correttamente il cliente CUST004 che ha 6 servizi scaduti")
    void testIdentifiesCustomerWithMoreThanMaxExpiredServices() {

        // arrange
        Map<String, Set<ServiceWithExpirationDTO>> customersMap = new HashMap<>();
        Set<ServiceWithExpirationDTO> expiredServices = Set.of(
                new ServiceWithExpirationDTO(CloudServiceType.HOSTING, LocalDate.of(2021, 4, 22)),
                new ServiceWithExpirationDTO(CloudServiceType.FIRMA_DIGITALE, LocalDate.of(2023, 6, 22)),
                new ServiceWithExpirationDTO(CloudServiceType.PEC, LocalDate.of(2024, 10, 3)),
                new ServiceWithExpirationDTO(CloudServiceType.CONSERVAZIONE_DIGITALE, LocalDate.of(2024, 12, 22)),
                new ServiceWithExpirationDTO(CloudServiceType.FATTURAZIONE, LocalDate.of(2022, 5, 22)),
                new ServiceWithExpirationDTO(CloudServiceType.SPID, LocalDate.of(2020, 3, 22))
        );
        customersMap.put("CUST004", expiredServices);

        CustomerWithExpiredServicesDTO dto = new CustomerWithExpiredServicesDTO(customersMap);
        when(cloudServiceService.getCustomersWithMaxExpiredServices(5)).thenReturn(dto);

        try {
            when(mapper.writeValueAsString(any())).thenAnswer(invocation -> {
                // Cattura l'oggetto che viene serializzato
                Object obj = invocation.getArgument(0);
                return "{\"test\":\"value\"}"; // Valore fittizio per il test
            });
        } catch (JsonProcessingException e) {
            fail("JSON serialization failed", e);
        }

        // act
        notificationRule.checkAndQueueNotifications();

        // assert
        verify(cloudServiceService).getCustomersWithMaxExpiredServices(5);

        verify(kafkaNotificationHandler).handle(notificationCaptor.capture());

        KafkaNotification capturedNotification = notificationCaptor.getValue();
        assertEquals("CUST004", capturedNotification.getPartitionKey());
        assertEquals("notifications", capturedNotification.getTopic());

        // Verifica che il contenuto della notifica includa informazioni sui servizi scaduti
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        try {
            verify(mapper).writeValueAsString(payloadCaptor.capture());
        } catch (JsonProcessingException e) {
            fail("JSON serialization failed", e);
        }

        Object serializedPayload = payloadCaptor.getValue();
        assertTrue(serializedPayload instanceof NotificationDTO,
                "Il payload dovrebbe essere un NotificationDTO");

        NotificationDTO notificationDTO = (NotificationDTO) serializedPayload;
        assertTrue(notificationDTO.getContent().contains("CUST004"),
                "Il contenuto della notifica dovrebbe menzionare il cliente CUST004");
        assertTrue(notificationDTO.getContent().contains("6 expired services"),
                "Il contenuto della notifica dovrebbe menzionare i 6 servizi scaduti");
    }

    @Test
    @DisplayName("Verifica che la regola non chiami l'handler con clienti che abbiano meno servizi scaduti del limite configurato")
    void testDoesNotIdentifyCustomersWithLessExpiredServices() {

        // arrange
        CustomerWithExpiredServicesDTO dto = new CustomerWithExpiredServicesDTO(new HashMap<>());
        when(cloudServiceService.getCustomersWithMaxExpiredServices(5)).thenReturn(dto);

        // act
        notificationRule.checkAndQueueNotifications();

        // assert
        verify(cloudServiceService).getCustomersWithMaxExpiredServices(5);
        verify(kafkaNotificationHandler, never()).handle(any());
    }

    @Test
    @DisplayName("Verifica che la regola generi correttamente il contenuto della notifica con il riepilogo dei servizi scaduti")
    void testGeneratesCorrectNotificationContent() {

        // arrange
        Map<String, Set<ServiceWithExpirationDTO>> customersMap = new HashMap<>();
        customersMap.put("CUST004", Set.of(
                new ServiceWithExpirationDTO(CloudServiceType.HOSTING, LocalDate.of(2021, 4, 22)),
                new ServiceWithExpirationDTO(CloudServiceType.FIRMA_DIGITALE, LocalDate.of(2023, 6, 22))
        ));

        CustomerWithExpiredServicesDTO dto = new CustomerWithExpiredServicesDTO(customersMap);
        when(cloudServiceService.getCustomersWithMaxExpiredServices(5)).thenReturn(dto);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        try {
            // Configura il mock per restituire un JSON che include il contenuto della notifica
            when(mapper.writeValueAsString(payloadCaptor.capture())).thenReturn("{\"test\":\"value\"}");
        } catch (JsonProcessingException e) {
            fail("Mock setup failed", e);
        }

        // act
        notificationRule.checkAndQueueNotifications();

        // assert
        verify(kafkaNotificationHandler).handle(notificationCaptor.capture());

        // Verifica che l'oggetto passato a writeValueAsString sia un NotificationDTO
        Object serializedPayload = payloadCaptor.getValue();
        assertTrue(serializedPayload instanceof NotificationDTO,
                "Il payload dovrebbe essere un NotificationDTO");

        // Verifica il contenuto della notifica
        NotificationDTO notificationDTO = (NotificationDTO) serializedPayload;
        assertTrue(notificationDTO.getContent().contains("CUST004"),
                "Il contenuto della notifica dovrebbe menzionare il cliente CUST004");
        assertTrue(notificationDTO.getContent().contains("2 expired services") ||
                        notificationDTO.getContent().contains("2 servizi scaduti"),
                "Il contenuto della notifica dovrebbe menzionare i 2 servizi scaduti");
        assertTrue(notificationDTO.getContent().contains(CloudServiceType.HOSTING.name()),
                "Il contenuto della notifica dovrebbe menzionare il servizio HOSTING");
        assertTrue(notificationDTO.getContent().contains(CloudServiceType.FIRMA_DIGITALE.name()),
                "Il contenuto della notifica dovrebbe menzionare il servizio FIRMA_DIGITALE");
        assertTrue(notificationDTO.getContent().contains("2021-04-22"),
                "Il contenuto della notifica dovrebbe menzionare la data di scadenza 2021-04-22");
        assertTrue(notificationDTO.getContent().contains("2023-06-22"),
                "Il contenuto della notifica dovrebbe menzionare la data di scadenza 2023-06-22");
    }

    @Test
    @DisplayName("Verifica che la regola invii correttamente una notifica di tipo KAFKA")
    void testSendsKafkaNotification() {

        // arrange
        Map<String, Set<ServiceWithExpirationDTO>> customersMap = new HashMap<>();
        customersMap.put("CUST004", Set.of(
                new ServiceWithExpirationDTO(CloudServiceType.HOSTING, LocalDate.of(2021, 4, 22))
        ));

        CustomerWithExpiredServicesDTO dto = new CustomerWithExpiredServicesDTO(customersMap);
        when(cloudServiceService.getCustomersWithMaxExpiredServices(5)).thenReturn(dto);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        try {
            when(mapper.writeValueAsString(payloadCaptor.capture())).thenReturn("{\"test\":\"value\"}");
        } catch (JsonProcessingException e) {
            fail("JSON serialization failed", e);
        }

        // act
        notificationRule.checkAndQueueNotifications();

        // assert
        verify(kafkaNotificationHandler).handle(notificationCaptor.capture());

        KafkaNotification capturedNotification = notificationCaptor.getValue();
        assertNotNull(capturedNotification, "La notifica non dovrebbe essere null");

        assertEquals(notificationRule.getNotificationTopic(), capturedNotification.getTopic(),
                "La notifica dovrebbe essere inviata al topic notifications");

        Object serializedPayload = payloadCaptor.getValue();
        assertTrue(serializedPayload instanceof NotificationDTO,
                "Il payload dovrebbe essere un NotificationDTO");

        NotificationDTO notificationDTO = (NotificationDTO) serializedPayload;
        assertEquals(NotificationType.KAFKA, notificationDTO.getType(),
                "La notifica dovrebbe essere di tipo KAFKA");

        String alertsCustomerExpiredTopic = notificationRule.getAlertsCustomerExpiredTopic();
        assertEquals(alertsCustomerExpiredTopic, notificationDTO.getRecipient(),
                "Il recipient della notifica dovrebbe essere il topic alerts.customer_expired");
    }

    @Test
    @DisplayName("Verifica che la regola gestisca correttamente gli errori di serializzazione")
    void testHandlesSerializationErrors() throws Exception {

        // arrange
        Map<String, Set<ServiceWithExpirationDTO>> customersMap = new HashMap<>();
        customersMap.put("CUST004", Set.of(
                new ServiceWithExpirationDTO(CloudServiceType.HOSTING, LocalDate.of(2021, 4, 22))
        ));

        CustomerWithExpiredServicesDTO dto = new CustomerWithExpiredServicesDTO(customersMap);
        when(cloudServiceService.getCustomersWithMaxExpiredServices(5)).thenReturn(dto);

        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Test serialization error") {});

        // act
        notificationRule.checkAndQueueNotifications();

        // assert
        verify(kafkaNotificationHandler, never()).handle(any());
    }

    @Test
    @DisplayName("Verifica che la regola gestisca correttamente gli errori di invio della notifica")
    void testHandlesNotificationSendingErrors() throws Exception {

        // arrange
        Map<String, Set<ServiceWithExpirationDTO>> customersMap = new HashMap<>();
        customersMap.put("CUST004", Set.of(
                new ServiceWithExpirationDTO(CloudServiceType.HOSTING, LocalDate.of(2021, 4, 22))
        ));

        CustomerWithExpiredServicesDTO dto = new CustomerWithExpiredServicesDTO(customersMap);
        when(cloudServiceService.getCustomersWithMaxExpiredServices(5)).thenReturn(dto);

        when(mapper.writeValueAsString(any())).thenReturn("{\"test\":\"value\"}");

        // simula un errore durante l'invio
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Test send failure"));

        // Crea un mock del KafkaProducer
        KafkaProducer mockProducer = mock(KafkaProducer.class);
        when(mockProducer.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);

        // Crea un nuovo KafkaNotificationHandler con il mock del KafkaProducer
        KafkaNotificationHandler testHandler = new KafkaNotificationHandler(mockProducer);

        // spia sul nuovo handler per verificare che saveForRetry venga chiamato
        KafkaNotificationHandler spyHandler = spy(testHandler);

        // sostituisce temporaneamente il KafkaNotificationHandler nella regola
        Field handlerField = ExpiredServicesNotificationRule.class.getDeclaredField("kafkaNotificationHandler");
        handlerField.setAccessible(true);
        Object originalHandler = handlerField.get(notificationRule);
        handlerField.set(notificationRule, spyHandler);

        try {

            // act
            notificationRule.checkAndQueueNotifications();

            // attende un po' per dare tempo al callback di essere eseguito
            Thread.sleep(100);

            // assert
            // verifica che il metodo send sia stato chiamato con i parametri corretti
            verify(mockProducer).send(
                    eq(notificationRule.getNotificationTopic()),
                    eq("CUST004"),
                    anyString()
            );

            // verifica che saveForRetry sia stato chiamato con una KafkaNotification
            verify(spyHandler).saveForRetry(any(KafkaNotification.class), any(Throwable.class));
        } finally {
            // Ripristina i componenti originali
            handlerField.set(notificationRule, originalHandler);
        }
    }
}
