package com.cimparato.csbm.dto.fileupload;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FileUploadDTO {
    private Long id;
    private String filename;
    private String fileHash;
    private LocalDateTime uploadDate;
    private String uploadedBy;
    private String status;
    private Integer totalRecords;
    private Integer validRecords;
    private Integer invalidRecords;
}
