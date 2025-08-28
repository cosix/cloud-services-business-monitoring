package com.cimparato.csbm.repository.projection;

import java.math.BigDecimal;

public record CustomerAverageSpend(String customerId, BigDecimal averageAmount) {}
