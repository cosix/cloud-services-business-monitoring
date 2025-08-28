package com.cimparato.csbm.service.notification.handler;

import com.cimparato.csbm.domain.notification.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class NotificationHandlerStrategy {

    private final Map<NotificationType, NotificationHandler> handlers;

    public NotificationHandlerStrategy(List<NotificationHandler> handlersList) {
        this.handlers = handlersList.stream()
                .collect(Collectors.toMap(NotificationHandler::supportedType , Function.identity()));

        log.info("Registered notification handlers: {}", handlers.keySet());
    }

    public NotificationHandler getHandler(NotificationType type) {

        NotificationHandler handler = handlers.get(type);

        if (handler == null) {
            throw new UnsupportedOperationException("No handler registered for notification type: " + type);
        }

        return handler;
    }

}
