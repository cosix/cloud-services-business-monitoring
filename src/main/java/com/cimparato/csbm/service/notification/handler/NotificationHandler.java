package com.cimparato.csbm.service.notification.handler;

import com.cimparato.csbm.service.notification.factory.BaseNotification;
import com.cimparato.csbm.domain.notification.NotificationType;
import com.cimparato.csbm.web.rest.errors.NotificationException;

// Definisce il contratto per il gestore di notifiche
public interface NotificationHandler {

    void handle(BaseNotification message) throws NotificationException;

    NotificationType supportedType();

}
