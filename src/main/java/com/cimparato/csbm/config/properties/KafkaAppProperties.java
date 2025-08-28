package com.cimparato.csbm.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaAppProperties {

    private TopicConfig topic = new TopicConfig();
    private ProducerConfig producer;
    private ConsumerConfig consumer;

    @Data
    public static class TopicConfig {
        private String notification;
        private String alertCustomerExpired;
        private int partitions;
        private int replicationFactor;
    }

    @Data
    public static class ProducerConfig {
        private RetryConfig retry;
    }

    @Data
    public static class ConsumerConfig {
        private RetryConfig retry;
        private GroupConfig group;
    }

    @Data
    public static class RetryConfig {
        private int attempts;
        private BackoffConfig backoff;
    }

    @Data
    public static class BackoffConfig {
        private long delay;
        private double multiplier;
    }

    @Data
    public static class GroupConfig {
        private String notification;
    }
}
