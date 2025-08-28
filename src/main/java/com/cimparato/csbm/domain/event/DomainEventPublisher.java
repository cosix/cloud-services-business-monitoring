package com.cimparato.csbm.domain.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
public class DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public DomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(DomainEvent event) {
        log.debug("Attempting to publish event: {}", event.getClass().getSimpleName());

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug("Transaction active, registering event for publication after commit");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.debug("Transaction committed, publishing event: {}", event.getClass().getSimpleName());
                    log.debug("Event published after transaction commit: {}", event.getClass().getSimpleName());
                    applicationEventPublisher.publishEvent(event);
                }
            });
        } else {
            log.debug("No active transaction, publishing event immediately");
            log.debug("Event published immediately: {}", event.getClass().getSimpleName());
            applicationEventPublisher.publishEvent(event);
        }
    }
}
