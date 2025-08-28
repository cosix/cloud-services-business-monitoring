package com.cimparato.csbm.service.notification.factory;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
public class KafkaNotification extends BaseNotification {
    private String topic;
    private String partitionKey;
    private String payload;
}
