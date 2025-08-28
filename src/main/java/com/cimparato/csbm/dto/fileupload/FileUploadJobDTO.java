package com.cimparato.csbm.dto.fileupload;

import com.cimparato.csbm.domain.enumeration.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileUploadJobDTO {
    private String filename;
    private String fileHash;
    private String jobId;
    private JobStatus jobStatus;
}
