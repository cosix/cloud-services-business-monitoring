package com.cimparato.csbm.service.file.parser;

import java.util.Arrays;

public enum CloudServiceCsvColumn {
    CUSTOMER_ID(0, "customer_id"),
    SERVICE_TYPE(1, "service_type"),
    ACTIVATION_DATE(2, "activation_date"),
    EXPIRATION_DATE(3, "expiration_date"),
    AMOUNT(4, "amount"),
    STATUS(5, "status");

    private final int position;
    private final String headerName;

    CloudServiceCsvColumn(int position, String headerName) {
        this.position = position;
        this.headerName = headerName;
    }

    public int getPosition() {
        return position;
    }

    public String getHeaderName() {
        return headerName;
    }

    public static CloudServiceCsvColumn fromPosition(int position) {
        return Arrays.stream(values())
                .filter(col -> col.getPosition() == position)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid column position: " + position));
    }
}
