package com.cimparato.csbm.dto.jobexecution;

import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobStatusDTO {
    private String jobId;
    private JobStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String fileHash;
    private String filename;
    private Integer totalRecords;
    private Integer validRecords;
    private Integer invalidRecords;

    @JsonInclude(Include.NON_NULL)
    private String errorMessage;
}
