package com.cimparato.csbm.service.notification.factory;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
public class EmailNotification extends BaseNotification {
    private String sender;
    private String recipient;
    private String subject;
    private String content;
}
