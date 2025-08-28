package com.cimparato.csbm.domain.event;

import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.dto.jobexecution.JobStatusDTO;

public class JobCreatedEvent extends DomainEvent {

    private final String jobId;
    private final JobStatus jobStatus;

    public JobCreatedEvent(JobStatusDTO jobStatusDTO) {
        super(jobStatusDTO);
        this.jobId = jobStatusDTO.getJobId();
        this.jobStatus = jobStatusDTO.getStatus();
    }

    public String getJobId() {
        return jobId;
    }

    public JobStatus getJobStatus() {
        return jobStatus;
    }
}
