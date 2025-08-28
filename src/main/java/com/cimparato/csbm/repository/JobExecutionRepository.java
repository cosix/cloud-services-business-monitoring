package com.cimparato.csbm.repository;

import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.domain.model.JobExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {
    Optional<JobExecution> findByJobId(String jobId);
    Page<JobExecution> findByFileUploadFileHash(String fileHash, Pageable pageable);
    List<JobExecution> findByStatus(JobStatus status);
}
