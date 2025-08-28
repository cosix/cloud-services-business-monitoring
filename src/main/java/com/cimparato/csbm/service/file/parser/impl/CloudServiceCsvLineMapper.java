package com.cimparato.csbm.service.file.parser.impl;

import com.cimparato.csbm.dto.cloudservice.CloudServiceDTO;
import com.cimparato.csbm.domain.enumeration.CloudServiceStatus;
import com.cimparato.csbm.domain.enumeration.CloudServiceType;
import com.cimparato.csbm.service.file.parser.CloudServiceCsvColumn;
import com.cimparato.csbm.service.file.parser.CsvLineMapper;
import com.cimparato.csbm.web.rest.errors.CsvMappingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CloudServiceCsvLineMapper implements CsvLineMapper<CloudServiceDTO> {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

    private static final List<String> allowedServiceStatus =
            Arrays.stream(CloudServiceStatus.values())
                    .map(CloudServiceStatus::name).toList();

    private static final List<String> allowedServices =
            Arrays.stream(CloudServiceType.values())
                    .map(CloudServiceType::name)
                    .toList();

    @Override
    public CloudServiceDTO mapLine(String[] line, int lineNumber) throws CsvMappingException {
        int expectedLength = CloudServiceCsvColumn.values().length;

        if (line.length != expectedLength) {
            throw new IllegalArgumentException("Invalid line format. Expected '" + expectedLength +"' columns.");
        }

        CloudServiceDTO dto = new CloudServiceDTO();

        // customer_id
        var customerId = line[0];
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("customer_id cannot be empty");
        }
        dto.setCustomerId(customerId.trim());

        // service_type
        var serviceType = line[1];
        if (serviceType == null || serviceType.trim().isEmpty()) {
            throw new IllegalArgumentException("service_type cannot be empty");
        }
        serviceType = serviceType.trim().toUpperCase();
        if (!allowedServices.contains(serviceType)) {
            var validServiceList = allowedServices.stream()
                    .collect(Collectors.joining(", "));

            throw new IllegalArgumentException(
                    String.format("service_type '%s' is not allowed. Please provide one of the following valid services: %s",
                            serviceType, validServiceList));
        }

        dto.setServiceType(CloudServiceType.valueOf(serviceType));

        // activation_date
        var activationDateString = line[2];
        if (activationDateString == null || activationDateString.trim().isEmpty()) {
            throw new IllegalArgumentException("activation_date cannot be empty");
        }
        LocalDate activationDate;
        try {
            activationDate = LocalDate.parse(line[2].trim(), DATE_FORMATTER);
            dto.setActivationDate(activationDate);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid activation_date format. Expected ISO date-time format (YYYY-MM-DD)");
        }

        // status
        var status = line[5];
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("status cannot be empty");
        }
        status = status.trim().toUpperCase();
        if (!allowedServiceStatus.contains(status)) {
            String validStatusList = allowedServiceStatus.stream()
                    .collect(Collectors.joining(", "));

            throw new IllegalArgumentException(
                    String.format("status '%s' is not allowed. Please provide one of the following valid statuses: %s",
                                  status, validStatusList));
        } else {
            dto.setStatus(CloudServiceStatus.valueOf(status));
        }

        // expiration_date
        var expirationDateString = line[3];
        if (expirationDateString == null || expirationDateString.trim().isEmpty()) {
            throw new IllegalArgumentException("expiration_date cannot be empty");
        }
        LocalDate expirationDate;
        try {
            expirationDate = LocalDate.parse(expirationDateString.trim(), DATE_FORMATTER);
            dto.setExpirationDate(expirationDate);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid expiration_date format. Expected ISO date-time format (YYYY-MM-DD)");
        }

        checkOnStatus(activationDate, expirationDate, status);

        // amount
        var amountString = line[4];
        if (amountString == null || amountString.trim().isEmpty()) {
            throw new IllegalArgumentException("amount cannot be empty");
        }
        try {
            var amount = new BigDecimal(amountString.trim());
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("amount cannot be negative");
            } else if (amount.compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalArgumentException("amount cannot be zero");
            } else {
                dto.setAmount(amount);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount format. Expected a number");
        }


        dto.setLineNumber(lineNumber);

        return dto;
    }

    private static void checkOnStatus(LocalDate activationDate, LocalDate expirationDate, String status) throws DateTimeParseException {
        var today = LocalDate.now();

        if (activationDate == null) {
            throw new IllegalArgumentException("activation_date cannot be null.");
        }

        if (status == null) {
            throw new IllegalArgumentException("status cannot be null.");
        }

        if (expirationDate.isBefore(activationDate)) {
            throw new IllegalArgumentException("expiration_date cannot be before activation_date.");
        }

        if (CloudServiceStatus.EXPIRED.name().equals(status)) {
            if (expirationDate.isAfter(today)) {
                throw new IllegalArgumentException("expiration_date cannot be greater than current day for a service " +
                        "with status " + status);
            }
        }

        if (CloudServiceStatus.ACTIVE.name().equals(status) || CloudServiceStatus.PENDING_RENEWAL.name().equals(status)) {
            if (expirationDate.isBefore(today)) {
                throw new IllegalArgumentException("expiration_date cannot be less than current day for a service " +
                        "with status " + status);
            }
        }

    }
}
