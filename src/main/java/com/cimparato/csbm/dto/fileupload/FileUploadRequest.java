package com.cimparato.csbm.dto.fileupload;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FileUploadRequest {
    @NotNull
    private String filename;

    private String uploadedBy;
}