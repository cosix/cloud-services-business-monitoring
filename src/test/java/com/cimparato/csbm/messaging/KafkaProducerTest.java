package com.cimparato.csbm.messaging;

import com.cimparato.csbm.config.properties.KafkaAppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private TaskScheduler kafkaRetryScheduler;

    @Mock
    private KafkaAppProperties kafkaAppProperties;

    @Mock
    private KafkaAppProperties.ProducerConfig producerConfig;

    @Mock
    private KafkaAppProperties.RetryConfig retryConfig;

    @Mock
    private KafkaAppProperties.BackoffConfig backoffConfig;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, String>> recordCaptor;

    private KafkaProducer kafkaProducer;

    @BeforeEach
    void setUp() {
        lenient().when(kafkaAppProperties.getProducer()).thenReturn(producerConfig);
        lenient().when(producerConfig.getRetry()).thenReturn(retryConfig);
        lenient().when(retryConfig.getAttempts()).thenReturn(3);
        lenient().when(retryConfig.getBackoff()).thenReturn(backoffConfig);
        lenient().when(backoffConfig.getDelay()).thenReturn(100L);
        lenient().when(backoffConfig.getMultiplier()).thenReturn(2.0);

        kafkaProducer = new KafkaProducer(kafkaTemplate, mapper, kafkaRetryScheduler, kafkaAppProperties);
    }


    @Test
    @DisplayName("Verifica che il producer invii correttamente i messaggi al topic configurato")
    void testSendMessageToConfiguredTopic() throws Exception {

        // arrange
        String topic = "test-topic";
        String key = "test-key";
        String payload = "{\"message\":\"test\"}";
        TestPayload testPayload = new TestPayload("test");

        when(mapper.writeValueAsString(testPayload)).thenReturn(payload);

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();

        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(topic, 0), 0, 0,
                System.currentTimeMillis(), 0L, 0, 0);

        SendResult<String, String> result = new SendResult<>(new ProducerRecord<>(topic, key, payload), metadata);

        future.complete(result);

        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        // act
        CompletableFuture<SendResult<String, String>> resultFuture = kafkaProducer.send(topic, key, testPayload);

        // assert
        verify(mapper).writeValueAsString(testPayload);
        verify(kafkaTemplate).send(topic, key, payload);
        assertNotNull(resultFuture);
        assertTrue(resultFuture.isDone());
        assertEquals(result, resultFuture.get());
    }

    @Test
    @DisplayName("Verifica che il producer utilizzi la chiave di partizione corretta")
    void testUsesCorrectPartitionKey() throws Exception {

        // arrange
        String topic = "test-topic";
        String key = "customer-123";
        String payload = "{\"message\":\"test\"}";
        TestPayload testPayload = new TestPayload("test");

        when(mapper.writeValueAsString(testPayload)).thenReturn(payload);

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        // act
        kafkaProducer.send(topic, key, testPayload);

        // assert
        verify(kafkaTemplate).send(topic, key, payload);
    }

    @Test
    @DisplayName("Verifica che il producer gestisca correttamente gli errori di serializzazione")
    void testHandlesSerializationErrors() throws Exception {

        // arrange
        String topic = "test-topic";
        String key = "test-key";
        TestPayload testPayload = new TestPayload("test");

        when(mapper.writeValueAsString(testPayload)).thenThrow(new JsonProcessingException("Test serialization error") {});

        // act
        CompletableFuture<SendResult<String, String>> resultFuture = kafkaProducer.send(topic, key, testPayload);

        // assert
        verify(mapper).writeValueAsString(testPayload);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        assertTrue(resultFuture.isCompletedExceptionally());
    }

    @Test
    @DisplayName("Verifica che il producer gestisca correttamente gli errori di invio")
    void testHandlesSendErrors() throws Exception {

        // arrange
        String topic = "test-topic";
        String key = "test-key";
        String payload = "{\"message\":\"test\"}";
        TestPayload testPayload = new TestPayload("test");
        RuntimeException sendException = new RuntimeException("Send failed");

        when(mapper.writeValueAsString(testPayload)).thenReturn(payload);

        // configura il comportamento per send(String, String, String)
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(sendException);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        // configura il comportamento per send(ProducerRecord)
        CompletableFuture<SendResult<String, String>> dltFuture = new CompletableFuture<>();
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(topic + ".DLT", 0), 0, 0,
                System.currentTimeMillis(), 0L, 0, 0);
        SendResult<String, String> dltResult = new SendResult<>(new ProducerRecord<>(topic + ".DLT", key, payload), metadata);
        dltFuture.complete(dltResult);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(dltFuture);

        // configura il comportamento dello scheduler per eseguire immediatamente il task
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(kafkaRetryScheduler).schedule(any(Runnable.class), any(Instant.class));

        // act
        CompletableFuture<SendResult<String, String>> resultFuture = kafkaProducer.send(topic, key, testPayload);

        // assert
        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), anyString());
        verify(kafkaTemplate).send(any(ProducerRecord.class));
        assertTrue(resultFuture.isCompletedExceptionally());
    }

    @Test
    @DisplayName("Verifica che il producer implementi correttamente il meccanismo di retry")
    void testImplementsRetryMechanism() throws Exception {

        // arrange
        String topic = "test-topic";
        String key = "test-key";
        String payload = "{\"message\":\"test\"}";
        TestPayload testPayload = new TestPayload("test");
        RuntimeException sendException = new RuntimeException("Send failed");

        when(mapper.writeValueAsString(testPayload)).thenReturn(payload);

        // prima chiamata fallisce
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(sendException);

        // seconda chiamata ha successo
        CompletableFuture<SendResult<String, String>> successFuture = new CompletableFuture<>();
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(topic, 0), 0, 0,
                System.currentTimeMillis(), 0L, 0, 0);
        SendResult<String, String> result = new SendResult<>(new ProducerRecord<>(topic, key, payload), metadata);
        successFuture.complete(result);

        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failedFuture)
                .thenReturn(successFuture);

        // configura il comportamento dello scheduler per eseguire immediatamente il task
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(kafkaRetryScheduler).schedule(any(Runnable.class), any(Instant.class));

        // act
        CompletableFuture<SendResult<String, String>> resultFuture = kafkaProducer.send(topic, key, testPayload);

        // assert
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), anyString());
        verify(kafkaRetryScheduler).schedule(any(Runnable.class), any(Instant.class));
        assertFalse(resultFuture.isCompletedExceptionally());
        assertEquals(result, resultFuture.get());
    }

    @Test
    @DisplayName("Verifica che il producer invii messaggi al dead letter topic quando tutti i tentativi falliscono")
    void testSendsToDeadLetterTopicAfterMaxRetries() throws Exception {

        // arrange
        String topic = "test-topic";
        String key = "test-key";
        String payload = "{\"message\":\"test\"}";
        TestPayload testPayload = new TestPayload("test");
        RuntimeException sendException = new RuntimeException("Send failed");

        when(mapper.writeValueAsString(testPayload)).thenReturn(payload);

        // tutte le chiamate falliscono
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(sendException);
        when(kafkaTemplate.send(eq(topic), anyString(), anyString())).thenReturn(failedFuture);

        // la chiamata al DLT ha successo
        CompletableFuture<SendResult<String, String>> dltFuture = new CompletableFuture<>();
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(topic + ".DLT", 0), 0, 0,
                System.currentTimeMillis(), 0L, 0, 0);
        SendResult<String, String> result = new SendResult<>(new ProducerRecord<>(topic + ".DLT", key, payload), metadata);
        dltFuture.complete(result);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(dltFuture);

        // configura il comportamento dello scheduler per eseguire immediatamente il task
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(kafkaRetryScheduler).schedule(any(Runnable.class), any(Instant.class));

        // act
        CompletableFuture<SendResult<String, String>> resultFuture = kafkaProducer.send(topic, key, testPayload);

        // assert
        verify(kafkaTemplate, times(3)).send(eq(topic), anyString(), anyString());
        verify(kafkaTemplate).send(recordCaptor.capture());

        ProducerRecord<String, String> capturedRecord = recordCaptor.getValue();
        assertEquals(topic + ".DLT", capturedRecord.topic());
        assertEquals(key, capturedRecord.key());
        assertEquals(payload, capturedRecord.value());
        assertTrue(resultFuture.isCompletedExceptionally());
    }

    // classe di supporto per i test
    private static class TestPayload {
        private final String message;

        public TestPayload(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
