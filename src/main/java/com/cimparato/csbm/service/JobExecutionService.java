package com.cimparato.csbm.service;

import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.domain.model.FileUpload;
import com.cimparato.csbm.domain.model.JobExecution;
import com.cimparato.csbm.repository.JobExecutionRepository;
import com.cimparato.csbm.web.rest.errors.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class JobExecutionService {

    private final JobExecutionRepository jobExecutionRepository;

    public JobExecutionService(JobExecutionRepository jobExecutionRepository) {
        this.jobExecutionRepository = jobExecutionRepository;
    }

    public JobExecution createJob(FileUpload fileUpload, String username) {
        String jobId = UUID.randomUUID().toString();

        JobExecution job = JobExecution.builder()
                .jobId(jobId)
                .status(JobStatus.PENDING)
                .startTime(LocalDateTime.now())
                .fileUpload(fileUpload)
                .createdBy(username)
                .build();

        return jobExecutionRepository.save(job);
    }

    @Transactional
    public JobExecution updateJobStatus(String jobId, JobStatus status, String errorMessage) {
        JobExecution job = getJobExecutionById(jobId);
        job.setStatus(status);

        if (status == JobStatus.COMPLETED || status == JobStatus.FAILED) {
            job.setEndTime(LocalDateTime.now());
        }

        if (errorMessage != null) {
            job.setErrorMessage(errorMessage);
        }

        log.info("Updated job {} status to {}", jobId, status);
        return jobExecutionRepository.save(job);
    }

    @Transactional(readOnly = true)
    public JobExecution getJobExecutionById(String jobId) {
        return jobExecutionRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job with id " + jobId + " not found"));
    }

    @Transactional(readOnly = true)
    public Page<JobExecution> getJobsByFileHash(String fileHash, Pageable pageable) {
        return jobExecutionRepository.findByFileUploadFileHash(fileHash, pageable);
    }

    @Transactional(readOnly = true)
    public List<JobExecution> getJobsWithStatus(JobStatus jobStatus) {
        return jobExecutionRepository.findByStatus(jobStatus);
    }

}
