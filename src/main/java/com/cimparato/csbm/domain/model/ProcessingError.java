package com.cimparato.csbm.domain.model;

import com.cimparato.csbm.domain.file.FileErrorType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "processing_errors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessingError {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "file_upload_id", nullable = false)
    private FileUpload fileUpload;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "raw_data")
    private String rawData;

    @Column(name = "error_message", nullable = false)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type")
    private FileErrorType errorType;
}
