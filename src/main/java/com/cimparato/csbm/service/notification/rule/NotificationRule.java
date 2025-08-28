package com.cimparato.csbm.service.notification.rule;

// Definisce il contratto per la creazione di regole da rispettare per l'invio di notifiche
public interface NotificationRule {

    // Verifica se la regola è applicabile e crea le notifiche necessarie
    void checkAndQueueNotifications();

    // Restituisce una descrizione della regola
    String getDescription();

}
