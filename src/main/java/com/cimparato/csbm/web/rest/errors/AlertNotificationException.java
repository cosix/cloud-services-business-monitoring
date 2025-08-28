package com.cimparato.csbm.web.rest.errors;

public class AlertNotificationException extends NotificationException {

    public AlertNotificationException(String message) {
        super(message);
    }

    public AlertNotificationException(String message, Throwable cause) {
        super(message, cause);
    }

}
