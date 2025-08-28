package com.cimparato.csbm.service.notification.handler.impl;

import com.cimparato.csbm.service.notification.factory.KafkaNotification;
import com.cimparato.csbm.service.notification.factory.BaseNotification;
import com.cimparato.csbm.domain.notification.NotificationType;
import com.cimparato.csbm.messaging.KafkaProducer;
import com.cimparato.csbm.service.notification.handler.NotificationHandler;
import com.cimparato.csbm.web.rest.errors.NotificationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaNotificationHandler implements NotificationHandler {

    private final KafkaProducer kafkaProducer;

    public KafkaNotificationHandler(KafkaProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void handle(BaseNotification baseNotification) throws NotificationException {

        var kafkaNotification = (KafkaNotification) baseNotification;
        String topic = kafkaNotification.getTopic();
        String key = kafkaNotification.getPartitionKey();
        String message = kafkaNotification.getPayload();

        log.info("Queuing message for customer: {} to topic: {}", key, topic);

        try {
            // Invia il messaggio in modo asincrono
            kafkaProducer.send(topic, key, message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send message for customer: {} to topic: {} with key: {}", key, topic, key, ex);
                            saveForRetry(kafkaNotification, ex);
                        } else {
                            RecordMetadata metadata = result.getRecordMetadata();
                            log.info("Kafka message sent successfully for customer: {} to topic: {}, partition: {}, offset: {}",
                                    key, topic, metadata.partition(), metadata.offset());
                        }
                    });

        } catch (Exception e) {
            // cattura errori durante la preparazione del messaggio
            log.error("Error preparing Kafka notification for topic: {} with key: {}", topic, key, e);
            throw new NotificationException("Failed to prepare Kafka notification", e);
        }
    }

    @Override
    public NotificationType supportedType() {
        return NotificationType.KAFKA;
    }

    public void saveForRetry(KafkaNotification notification, Throwable ex) {
        // TODO: Implementare logica di fallback: salvare la notifica nel DB con stato "FAILED" per un tentativo successivo
    }
}
