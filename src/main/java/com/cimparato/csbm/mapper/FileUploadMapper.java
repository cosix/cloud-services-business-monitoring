package com.cimparato.csbm.mapper;

import com.cimparato.csbm.domain.model.FileUpload;
import com.cimparato.csbm.domain.model.ProcessingError;
import com.cimparato.csbm.dto.fileupload.FileUploadRequest;
import com.cimparato.csbm.dto.fileupload.FileUploadDTO;
import com.cimparato.csbm.dto.fileupload.FileUploadSummaryDTO;
import com.cimparato.csbm.dto.processingerror.ProcessingErrorDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {ProcessingErrorMapper.class})
public interface FileUploadMapper {

    FileUploadDTO toDto(FileUpload entity);

    @Mapping(target = "processingErrors", expression = "java(sortProcessingErrors(entity.getProcessingErrors()))")
    FileUploadSummaryDTO toSummaryDto(FileUpload entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uploadDate", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "totalRecords", ignore = true)
    @Mapping(target = "validRecords", ignore = true)
    @Mapping(target = "invalidRecords", ignore = true)
    @Mapping(target = "serviceMappings", ignore = true)
    @Mapping(target = "processingErrors", ignore = true)
    FileUpload toEntity(FileUploadRequest dto);

    @Mapping(target = "serviceMappings", ignore = true)
    @Mapping(target = "processingErrors", ignore = true)
    FileUpload toEntity(FileUploadDTO dto);

    default List<ProcessingErrorDTO> sortProcessingErrors(Set<ProcessingError> errors) {
        if (errors == null) {
            return null;
        }
        ProcessingErrorMapper errorMapper = Mappers.getMapper(ProcessingErrorMapper.class);
        return errors.stream()
                .map(errorMapper::toDto)
                .sorted(Comparator.comparing(ProcessingErrorDTO::getLineNumber,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }
}
