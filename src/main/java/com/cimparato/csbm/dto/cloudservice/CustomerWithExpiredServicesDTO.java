package com.cimparato.csbm.dto.cloudservice;

import java.util.Map;
import java.util.Set;

public record CustomerWithExpiredServicesDTO(Map<String, Set<ServiceWithExpirationDTO>> map) {}
