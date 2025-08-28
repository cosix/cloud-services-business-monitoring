package com.cimparato.csbm.web.rest.errors;

public class EmailNotificationException extends NotificationException {

    public EmailNotificationException(String message) {
        super(message);
    }

    public EmailNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
