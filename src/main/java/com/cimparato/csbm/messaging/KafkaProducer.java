package com.cimparato.csbm.messaging;

import com.cimparato.csbm.config.properties.KafkaAppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class KafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper;
    private final TaskScheduler kafkaRetryScheduler;
    private final KafkaAppProperties kafkaAppProperties;

    public KafkaProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper mapper,
            @Qualifier("kafkaRetryScheduler") TaskScheduler kafkaRetryScheduler,
            KafkaAppProperties kafkaAppProperties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = mapper;
        this.kafkaRetryScheduler = kafkaRetryScheduler;
        this.kafkaAppProperties = kafkaAppProperties;
    }

    /**
     * Invia un messaggio a Kafka con una chiave di partizione e un numero specifico di tentativi
     *
     * Questo metodo serializza l'oggetto payload in formato JSON e lo invia al topic Kafka
     * specificato, utilizzando la chiave fornita per determinare la partizione. In caso di
     * errori durante l'invio, verrà riprovato l'invio fino a raggiungere il numero
     * massimo di tentativi specificato.
     *
     * @param topic Il topic Kafka a cui inviare il messaggio
     * @param key La chiave di partizione del messaggio
     * @param payload L'oggetto da serializzare e inviare come messaggio
     * @return CompletableFuture che si completa con il risultato dell'invio o con un'eccezione
     */
    public CompletableFuture<SendResult<String, String>> send(String topic, String key, Object payload) {
        try {

            String message = mapper.writeValueAsString(payload);

            CompletableFuture<SendResult<String, String>> resultFuture = new CompletableFuture<>();

            var totalAttempts = kafkaAppProperties.getProducer().getRetry().getAttempts();

            sendWithRetry(topic, key, message, 0, totalAttempts, resultFuture);

            return resultFuture;

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message for topic: {}, key: {}", topic, key, e);
            CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Implementa un meccanismo di retry asincrono per l'invio di messaggi a Kafka.
     *
     * Questo metodo utilizza uno scheduler dedicato per gestire i tentativi di invio in modo
     * non bloccante. Quando un tentativo fallisce, il prossimo tentativo viene programmato
     * dopo un intervallo calcolato con backoff esponenziale, che aumenta progressivamente
     * il tempo di attesa tra i tentativi.
     *
     * Il flusso di esecuzione è il seguente:
     * 1. Tenta di inviare il messaggio al topic specificato
     * 2. Se l'invio ha successo, completa il CompletableFuture con il risultato
     * 3. Se l'invio fallisce e ci sono ancora tentativi disponibili:
     *    - Calcola il tempo di backoff
     *    - Schedula il prossimo tentativo dopo il backoff
     * 4. Se tutti i tentativi falliscono:
     *    - Gestisce l'errore (inviando a un dead letter topic)
     *    - Completa eccezionalmente il CompletableFuture
     *
     * @param topic topic Kafka a cui inviare il messaggio
     * @param key chiave di partizione del messaggio
     * @param message contenuto del messaggio da inviare
     * @param currentAttempt numero del tentativo corrente
     * @param totalAttempts numero totale di tentativi da effettuare
     * @param resultFuture CompletableFuture che verrà completato con il risultato finale
     */
    private void sendWithRetry(
            String topic, String key, String message, int currentAttempt, int totalAttempts,
            CompletableFuture<SendResult<String, String>> resultFuture) {

        log.debug("Sending message to topic: {}, key: {}, attempt: {}/{}",
                topic, key, currentAttempt + 1, totalAttempts);

        kafkaTemplate.send(topic, key, message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {

                        // Successo
                        log.debug("Message sent successfully to topic: {}, partition: {}, offset: {}",
                                topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());

                        resultFuture.complete(result);

                    } else {

                        // errore
                        log.error("Failed to send message to topic: {}, key: {}, attempt: {}/{}",
                                topic, key, currentAttempt + 1, totalAttempts, ex);

                        if (currentAttempt < totalAttempts - 1) {

                            // calcola backoff
                            long backoffMs = calculateBackoff(currentAttempt);
                            log.info("Scheduling retry in {} ms (attempt {}/{})", backoffMs, currentAttempt + 2, totalAttempts);

                            // usa lo scheduler dedicato per i retry
                            kafkaRetryScheduler.schedule(
                                    () -> sendWithRetry(topic, key, message, currentAttempt + 1, totalAttempts, resultFuture),
                                    Instant.now().plusMillis(backoffMs)
                            );

                        } else {

                            log.error("Max attempts reached for topic: {}, key: {}", topic, key);
                            log.debug("Completing future exceptionally with exception: {}", ex.getMessage());
                            handleError(topic, key, message, ex);
                            resultFuture.completeExceptionally(ex);

                        }
                    }
                });
    }

    /**
     * Calcola il tempo di attesa per il backoff esponenziale.
     *
     * @param retryCount Il numero di tentativi già effettuati
     * @return Il tempo di attesa in millisecondi
     */
    private long calculateBackoff(int retryCount) {
        double multiplier = kafkaAppProperties.getProducer().getRetry().getBackoff().getMultiplier();
        long initialDelay = kafkaAppProperties.getProducer().getRetry().getBackoff().getDelay();

        // Se i valori di configurazione sono 0 o null, usa valori di default
        if (initialDelay <= 0) initialDelay = 100;
        if (multiplier <= 0) multiplier = 2.0;

        // Calcola il backoff usando la formula: initialDelay * (multiplier ^ retryCount)
        long delay = (long) (initialDelay * Math.pow(multiplier, retryCount));

        // Limita il backoff massimo a 5 minuti
        return Math.min(delay, 300000); // 5 minuti in ms
    }

    /**
     * Gestisce l'errore dopo che tutti i tentativi di invio sono falliti.
     *
     * Questo metodo invia il messaggio fallito a un dead letter topic con
     * metadati aggiuntivi sugli headers per facilitare il debugging e la gestione
     * degli errori. Il DLT ha lo stesso nome del topic originale con il suffisso ".DLT".
     *
     * @param topic topic originale a cui si stava tentando di inviare il messaggio
     * @param key chiave di partizione del messaggio
     * @param message contenuto del messaggio
     * @param ex eccezione che ha causato il fallimento
     */
    private void handleError(String topic, String key, String message, Throwable ex) {
        String deadLetterTopic = topic + ".DLT";

        ProducerRecord<String, String> record = new ProducerRecord<>(deadLetterTopic, key, message);
        record.headers().add("error-message", ex.getMessage().getBytes(StandardCharsets.UTF_8));
        record.headers().add("original-topic", topic.getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", Instant.now().toString().getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record)
                .whenComplete((result, dlqEx) -> {
                    if (dlqEx != null) {
                        log.error("Failed to send message to dead letter topic: {}", deadLetterTopic, dlqEx);
                    } else {
                        log.info("Message sent to dead letter topic: {}", deadLetterTopic);
                    }
                });
    }

}
