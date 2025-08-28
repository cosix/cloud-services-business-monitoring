package com.cimparato.csbm.domain.model;

import com.cimparato.csbm.domain.enumeration.JobStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_execution")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, unique = true)
    private String jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "file_path")
    private String filePath;

    @ManyToOne
    @JoinColumn(name = "file_upload_id", nullable = false)
    private FileUpload fileUpload;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @PrePersist
    public void prePersist() {
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
        if (status == null) {
            status = JobStatus.PENDING;
        }
    }
}
