package com.cimparato.csbm.messaging;

import com.cimparato.csbm.dto.notification.NotificationDTO;
import com.cimparato.csbm.service.MessageDeduplicationService;
import com.cimparato.csbm.service.notification.NotificationManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaConsumer {

    private final ObjectMapper mapper;
    private final NotificationManager notificationManager;
    private final MessageDeduplicationService messageDeduplicationService;

    public KafkaConsumer(
            ObjectMapper mapper,
            NotificationManager notificationManager,
            MessageDeduplicationService messageDeduplicationService
    ) {
        this.mapper = mapper;
        this.notificationManager = notificationManager;
        this.messageDeduplicationService = messageDeduplicationService;
    }

    /**
     * Consuma e processa i messaggi di notifica dal topic Kafka configurato.
     *
     * Questo metodo implementa un meccanismo di deduplicazione per garantire che ogni notifica
     * venga elaborata una sola volta, anche in presenza di messaggi duplicati o ritrasmissioni
     * dovute al meccanismo di retry di Kafka. Il processo è il seguente:
     *
     * 1. Rimuove eventuali caratteri di escape dal messaggio JSON
     * 2. Deserializza il messaggio in un oggetto NotificationDTO
     * 3. Genera un ID univoco basato sul contenuto del messaggio
     * 4. Verifica se il messaggio è già stato elaborato in precedenza
     * 5. Se il messaggio è nuovo, lo elabora e lo marca come processato
     * 6. Se il messaggio è già stato elaborato, lo salta ma committa comunque l'offset
     *
     * In caso di errore durante l'elaborazione, viene lanciata un'eccezione che attiva
     * il meccanismo di retry configurato in RetryTopicConfiguration. Questo garantisce
     * che i messaggi che falliscono temporaneamente possano essere riprocessati.
     *
     * @param message Il messaggio JSON ricevuto dal topic Kafka
     * @throws RuntimeException Se si verifica un errore durante l'elaborazione del messaggio,
     * attivando il meccanismo di retry
     */
    @KafkaListener(
            topics = "${app.kafka.topic.notification}",
            groupId = "${app.kafka.consumer.group.notification}"
    )
    public void consumeNotification(String message,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                    @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Consuming message: topic={}, partition={}, offset={}, message={}", topic, partition, offset, message);

        try {

            message = removeEscape(message);

            NotificationDTO notification = mapper.readValue(message, NotificationDTO.class);

            String messageId = messageDeduplicationService.generateMessageId(notification);

            if (messageDeduplicationService.isProcessed(messageId)) {
                log.info("Skipping already processed notification: {}", messageId);
                return;
            }

            notificationManager.notifyUser(notification);

            messageDeduplicationService.markAsProcessed(messageId);

        } catch (Exception e) {
            log.error("Error processing notification: {}", e.getMessage(), e);
            // L'eccezione attiverà il meccanismo di retry
            throw new RuntimeException("Failed to process notification", e);
        }

    }

    private String removeEscape(String message) throws JsonProcessingException {
        if (message.startsWith("\"") && message.endsWith("\"")) {
            message = mapper.readValue(message, String.class);
            log.debug("Unescaped message: [{}]", message);
        }
        return message;
    }
}
