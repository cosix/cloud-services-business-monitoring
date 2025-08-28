package com.cimparato.csbm.domain.model;

import com.cimparato.csbm.domain.file.FileOperationType;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "service_file_relation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceFileRelation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "service_id", nullable = false)
    private CloudService service;

    @ManyToOne
    @JoinColumn(name = "file_upload_id", nullable = false)
    private FileUpload fileUpload;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private FileOperationType operationType;

    @Column(name = "line_number")
    private Integer lineNumber;
}
