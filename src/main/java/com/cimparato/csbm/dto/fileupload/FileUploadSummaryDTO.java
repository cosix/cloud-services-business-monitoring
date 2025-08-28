package com.cimparato.csbm.dto.fileupload;

import com.cimparato.csbm.dto.processingerror.ProcessingErrorDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FileUploadSummaryDTO {
    private String fileHash;
    private String filename;
    private LocalDateTime uploadDate;
    private String uploadedBy;
    private String status;
    private Integer totalRecords;
    private Integer validRecords;
    private Integer invalidRecords;

    @JsonInclude(Include.NON_EMPTY)
    private List<ProcessingErrorDTO> processingErrors;
}
