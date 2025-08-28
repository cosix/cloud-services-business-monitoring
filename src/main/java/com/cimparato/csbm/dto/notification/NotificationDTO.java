package com.cimparato.csbm.dto.notification;

import com.cimparato.csbm.domain.notification.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationDTO {
    private NotificationType type;
    private String customerId;
    private String sender;
    private String recipient;
    private String subject;
    private String content;
    private LocalDateTime createdAt;
}
