package com.cimparato.csbm.dto.cloudservice;

import com.cimparato.csbm.domain.enumeration.CloudServiceStatus;
import com.cimparato.csbm.domain.enumeration.CloudServiceType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CloudServiceDTO {
    private Long id;
    private String customerId;
    private CloudServiceType serviceType;
    private LocalDate activationDate;
    private LocalDate expirationDate;
    private BigDecimal amount;
    private CloudServiceStatus status;
    private LocalDateTime lastUpdated;
    private int lineNumber;
}
