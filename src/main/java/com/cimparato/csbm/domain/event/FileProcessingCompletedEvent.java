package com.cimparato.csbm.domain.event;

import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.dto.fileupload.FileUploadJobDTO;

public class FileProcessingCompletedEvent extends DomainEvent {

    private final String fileHash;
    private final String filename;
    private final String jobId;
    private final JobStatus jobStatus;

    public FileProcessingCompletedEvent(FileUploadJobDTO fileUploadJobDTO) {
        super(fileUploadJobDTO);
        this.fileHash = fileUploadJobDTO.getFileHash();
        this.filename = fileUploadJobDTO.getFilename();
        this.jobId = fileUploadJobDTO.getJobId();
        this.jobStatus = fileUploadJobDTO.getJobStatus();
    }

    public String getFileHash() {
        return fileHash;
    }

    public String getFilename() {
        return filename;
    }

    public String getJobId() {
        return jobId;
    }

    public JobStatus getJobStatus() {
        return jobStatus;
    }
}
