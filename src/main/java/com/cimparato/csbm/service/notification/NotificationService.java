package com.cimparato.csbm.service.notification;

import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.config.properties.KafkaAppProperties;
import com.cimparato.csbm.domain.model.Notification;
import com.cimparato.csbm.domain.notification.NotificationStatus;
import com.cimparato.csbm.mapper.NotificationMapper;
import com.cimparato.csbm.repository.NotificationRepository;
import com.cimparato.csbm.dto.notification.NotificationDTO;
import com.cimparato.csbm.service.notification.handler.impl.KafkaNotificationHandler;
import com.cimparato.csbm.web.rest.errors.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;

@Slf4j
@Service
public class NotificationService {

    private final AppProperties appProperties;
    private final KafkaAppProperties kafkaAppProperties;
    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final KafkaNotificationHandler kafkaNotificationSender;

    public NotificationService(
            AppProperties appProperties,
            KafkaAppProperties kafkaAppProperties,
            NotificationRepository notificationRepository,
            NotificationMapper notificationMapper,
            KafkaNotificationHandler kafkaNotificationSender
    ) {
        this.appProperties = appProperties;
        this.kafkaAppProperties = kafkaAppProperties;
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
        this.kafkaNotificationSender = kafkaNotificationSender;
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> findAll() {
        return notificationMapper.toDtoList(notificationRepository.findAll());
    }

    @Transactional(readOnly = true)
    public NotificationDTO findById(Long id) {
        return notificationRepository.findById(id)
                .map(notificationMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> findByCustomerId(String customerId) {
        return notificationRepository.findByCustomerId(customerId)
                .stream()
                .map(notificationMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Notification> findFailed() {
        var pageRequest = PageRequest.of(0, 100);
        var totalAttempts = kafkaAppProperties.getConsumer().getRetry().getAttempts();
        return notificationRepository.findByStatusAndRetryCountLessThan(NotificationStatus.FAILED, totalAttempts, pageRequest);
    }

    @Transactional(readOnly = true)
    public List<Notification> findByStatus(NotificationStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        var pageRequest = PageRequest.of(0, 100);
        return notificationRepository.findByStatus(status, pageRequest);
    }

    public List<Notification> saveAll(List<Notification> notifications) {
        if (notifications == null) {
            throw new IllegalArgumentException("notifications cannot be null");
        }
        return notificationRepository.saveAll(notifications)
                .stream()
                .toList();
    }

    public Notification save(Notification notification) {
        if (notification == null) {
            throw new IllegalArgumentException("notification cannot be null");
        }
        return notificationRepository.save(notification);
    }

    public NotificationDTO saveFromDTO(NotificationDTO notificationDTO) {
        if (notificationDTO == null) {
            throw new IllegalArgumentException("notificationDTO cannot be null");
        }
        var notificationToSave = notificationMapper.toEntity(notificationDTO);
        var notification = notificationRepository.save(notificationToSave);
        return notificationMapper.toDto(notification);
    }

}

