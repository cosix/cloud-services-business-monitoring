package com.cimparato.csbm.dto.processingerror;

import com.cimparato.csbm.domain.file.FileErrorType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessingErrorCreateDTO {
    private Long fileUploadId;
    private Integer lineNumber;
    private String rawData;
    private String errorMessage;
    private FileErrorType errorType;
}
