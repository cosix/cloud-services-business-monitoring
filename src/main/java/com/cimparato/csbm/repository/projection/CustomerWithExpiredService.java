package com.cimparato.csbm.repository.projection;

import java.time.LocalDate;

public interface CustomerWithExpiredService {
    String getCustomerId();
    String getServiceType();
    LocalDate getExpirationDate();
}
