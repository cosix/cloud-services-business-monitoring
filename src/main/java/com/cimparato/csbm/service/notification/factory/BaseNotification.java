package com.cimparato.csbm.service.notification.factory;

import com.cimparato.csbm.domain.notification.NotificationStatus;
import com.cimparato.csbm.domain.notification.NotificationType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@SuperBuilder
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = EmailNotification.class, name = "EMAIL"),
        @JsonSubTypes.Type(value = KafkaNotification.class, name = "KAFKA")
})
public abstract class BaseNotification {
        private NotificationType type;
        private String customerId;
        private LocalDateTime createdAt;
        private LocalDateTime sentAt;
        private NotificationStatus status;
        private String errorMessage;
}
