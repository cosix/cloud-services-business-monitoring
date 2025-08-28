package com.cimparato.csbm.dto.cloudservice;

import com.cimparato.csbm.domain.enumeration.CloudServiceType;

import java.time.LocalDate;

public record ServiceWithExpirationDTO(CloudServiceType serviceType, LocalDate expirationDate) {}
