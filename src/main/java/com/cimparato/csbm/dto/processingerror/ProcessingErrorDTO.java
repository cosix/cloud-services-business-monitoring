package com.cimparato.csbm.dto.processingerror;

import com.cimparato.csbm.domain.file.FileErrorType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProcessingErrorDTO {
    private Integer lineNumber;
    private String rawData;
    private String errorMessage;
    private LocalDateTime createdAt;
    private FileErrorType errorType;
}
