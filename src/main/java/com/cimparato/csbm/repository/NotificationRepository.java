package com.cimparato.csbm.repository;

import com.cimparato.csbm.domain.model.Notification;
import com.cimparato.csbm.domain.notification.NotificationStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByStatus(NotificationStatus status, PageRequest pageRequest);
    List<Notification> findByStatusAndRetryCountLessThan(NotificationStatus status, Integer maxRetries, PageRequest pageRequest);
    List<Notification> findByCustomerId(String customerId);
}
