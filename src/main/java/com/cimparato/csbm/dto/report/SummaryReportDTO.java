package com.cimparato.csbm.dto.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SummaryReportDTO {
    private Map<String, Long> activeServicesByType;
    private Map<String, BigDecimal> averageSpendingPerCustomer;
    private List<String> customersWithMultipleExpiredServices;
    private List<String> customersWithServicesExpiringInNext15Days;
}
