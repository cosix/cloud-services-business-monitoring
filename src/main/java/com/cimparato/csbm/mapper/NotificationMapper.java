package com.cimparato.csbm.mapper;

import com.cimparato.csbm.domain.model.Notification;
import com.cimparato.csbm.dto.notification.NotificationDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationDTO toDto(Notification entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "sentAt", ignore = true)
    Notification toEntity(NotificationDTO dto);

    List<NotificationDTO> toDtoList(List<Notification> entities);

}
