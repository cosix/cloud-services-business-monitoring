package com.cimparato.csbm.service.file.validator.impl;

import com.cimparato.csbm.service.file.parser.CloudServiceCsvColumn;
import com.cimparato.csbm.service.file.validator.FileValidationRule;
import com.opencsv.CSVReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class CloudServiceCsvHeaderRuleFile implements FileValidationRule {

    private final List<String> errors = new ArrayList<>();

    @Override
    public boolean validate(MultipartFile file) {
        errors.clear();

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            String[] header = reader.readNext();
            return validateHeader(header);
        } catch (Exception e) {
            log.error("Error validating CSV header: {}", e.getMessage(), e);
            errors.add("Error reading CSV file: " + e.getMessage());
            return false;
        }
    }

    private boolean validateHeader(String[] header) {
        if (header == null) {
            errors.add("CSV header is null");
            return false;
        }

        int expectedLength = CloudServiceCsvColumn.values().length;
        if (header.length != expectedLength) {
            errors.add("Invalid CSV header length: expected " + expectedLength + " columns, found " + header.length);
            return false;
        }

        for (CloudServiceCsvColumn column : CloudServiceCsvColumn.values()) {
            int position = column.getPosition();

            if (position >= header.length) {
                errors.add("CSV header is too short, missing column at position " + position);
                continue;
            }

            String expectedName = column.getHeaderName();
            String actualName = header[position];
            if (actualName == null || actualName.trim().isEmpty()) {
                errors.add("Invalid column at position " + position +
                        ": expected '" + expectedName + "', found '" + actualName + "'");
            } else {
                actualName = header[position].trim();
            }

            if (!actualName.equalsIgnoreCase(expectedName)) {
                String normalizedExpected = normalizeColumnName(expectedName);
                String normalizedActual = normalizeColumnName(actualName);

                if (!normalizedExpected.equals(normalizedActual)) {
                    errors.add("Invalid column at position " + position +
                            ": expected '" + expectedName + "', found '" + actualName + "'");
                }
            }
        }

        return errors.isEmpty();
    }

    private String normalizeColumnName(String columnName) {
        if (columnName == null) {
            return "";
        }

        // Rimuove tutti i caratteri non alfanumerici e converte tutto in minuscolo
        return columnName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    @Override
    public String getErrorMessage() {
        return String.join("; ", errors);
    }

    @Override
    public int getOrder() {
        return 200; // Eseguito dopo FileExtensionRule
    }
}
