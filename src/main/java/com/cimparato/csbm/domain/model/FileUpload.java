package com.cimparato.csbm.domain.model;

import com.cimparato.csbm.domain.file.FileUploadStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "file_uploads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(name = "file_hash", length = 32)
    private String fileHash;

    @Column(name = "upload_date", nullable = false)
    private LocalDateTime uploadDate;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileUploadStatus status;

    @Column(name = "total_records")
    private Integer totalRecords;

    @Column(name = "valid_records")
    private Integer validRecords;

    @Column(name = "invalid_records")
    private Integer invalidRecords;

    @OneToMany(mappedBy = "fileUpload", cascade = CascadeType.ALL)
    private Set<ServiceFileRelation> serviceMappings = new HashSet<>();

    @OneToMany(mappedBy = "fileUpload", cascade = CascadeType.ALL)
    private Set<ProcessingError> processingErrors = new HashSet<>();

    @OneToMany(mappedBy = "fileUpload", cascade = CascadeType.ALL)
    private Set<JobExecution> jobExecutions = new HashSet<>();

    public boolean canBeReprocessed() {
        return status.equals(FileUploadStatus.CANCELLED) || status.equals(FileUploadStatus.FAILED);
    }
}
