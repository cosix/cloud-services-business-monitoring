package com.cimparato.csbm.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean
    public NewTopic notificationTopic(
            @Value("${app.kafka.topic.notification}") String topicName,
            @Value("${app.kafka.topic.partitions}") int numPartitions,
            @Value("${app.kafka.topic.replication-factor}") short replicationFactor) {
        return new NewTopic(topicName, numPartitions, replicationFactor);
    }

    @Bean
    public NewTopic alertCustomerExpired(
            @Value("${app.kafka.topic.alert-customer-expired}") String topicName,
            @Value("${app.kafka.topic.partitions}") int numPartitions,
            @Value("${app.kafka.topic.replication-factor}") short replicationFactor) {
        return new NewTopic(topicName, numPartitions, replicationFactor);
    }

    @Bean
    public RetryTopicConfiguration retryTopicConfiguration(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.consumer.retry.attempts}") int attempts,
            @Value("${app.kafka.consumer.retry.backoff.delay}") long delay,
            @Value("${app.kafka.consumer.retry.backoff.multiplier}") double multiplier,
            @Value("${app.kafka.topic.notification}") String notificationTopic,
            @Value("${app.kafka.topic.partitions}") int numPartitions,
            @Value("${app.kafka.topic.replicationFactor}") int replicationFactor) {

        return RetryTopicConfigurationBuilder
                .newInstance()
                .exponentialBackoff(delay, multiplier, 300000) // max backoff 5 minuti
                .maxAttempts(attempts)
                .includeTopic(notificationTopic)
                .retryTopicSuffix("-retry") // suffisso per i topic di retry
                .dltSuffix("-dlt") // suffisso per i dead letter topic
                .autoCreateTopics(true, numPartitions, (short) replicationFactor)
                .dltProcessingFailureStrategy(DltStrategy.FAIL_ON_ERROR)
                .create(kafkaTemplate);
    }

}
