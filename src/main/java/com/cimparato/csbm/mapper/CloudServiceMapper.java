package com.cimparato.csbm.mapper;

import com.cimparato.csbm.domain.model.CloudService;
import com.cimparato.csbm.dto.cloudservice.CloudServiceDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public interface CloudServiceMapper {

    CloudServiceDTO toDto(CloudService entity);

    @Mapping(target = "fileMappings", ignore = true)
    @Mapping(target = "lastUpdated", expression = "java(getCurrentTime())")
    CloudService toEntity(CloudServiceDTO dto);

    @Named("getCurrentTime")
    default LocalDateTime getCurrentTime() {
        return LocalDateTime.now();
    }

}
