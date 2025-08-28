package com.cimparato.csbm.mapper;

import com.cimparato.csbm.domain.model.FileUpload;
import com.cimparato.csbm.domain.model.ProcessingError;
import com.cimparato.csbm.dto.processingerror.ProcessingErrorCreateDTO;
import com.cimparato.csbm.dto.processingerror.ProcessingErrorDTO;
import com.cimparato.csbm.domain.file.FileErrorType;
import com.cimparato.csbm.service.file.parser.ParsingError;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper(componentModel = "spring")
public interface ProcessingErrorMapper {

    ProcessingErrorDTO toDto(ProcessingError entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fileUpload", source = "fileUploadId")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    ProcessingError toEntity(ProcessingErrorCreateDTO dto);

    default FileUpload mapFileUploadId(Long fileUploadId) {
        if (fileUploadId == null) {
            return null;
        }
        FileUpload fileUpload = new FileUpload();
        fileUpload.setId(fileUploadId);
        return fileUpload;
    }

    default List<ProcessingError> convertParsingErrorsTo(List<ParsingError> parsingErrors, FileUpload fileUpload) {
        return Optional.ofNullable(parsingErrors)
                .orElse(List.of())
                .stream()
                .map(pa ->
                        ProcessingError.builder()
                                .fileUpload(fileUpload)
                                .lineNumber(pa.getLineNumber())
                                .rawData(pa.getRawData())
                                .errorMessage(pa.getErrorMessage())
                                .errorType(FileErrorType.PARSING_ERROR)
                                .createdAt(LocalDateTime.now())
                                .build())
                .toList();
    }
}
