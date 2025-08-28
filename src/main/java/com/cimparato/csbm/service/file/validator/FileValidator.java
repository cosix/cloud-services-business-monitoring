package com.cimparato.csbm.service.file.validator;

import com.cimparato.csbm.domain.file.ValidationResult;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class FileValidator {
    private final List<FileValidationRule> fileValidationRules;

    public FileValidator(List<FileValidationRule> fileValidationRules) {
        // Ordina le regole in base al loro ordine
        this.fileValidationRules = fileValidationRules.stream()
                .sorted(Comparator.comparing(FileValidationRule::getOrder))
                .collect(Collectors.toList());
    }

    public ValidationResult validate(MultipartFile file) {
        ValidationResult result = new ValidationResult();

        for (FileValidationRule rule : fileValidationRules) {
            if (!rule.validate(file)) {
                result.addError(rule.getErrorMessage());
                return result; // fermarti alla prima regola fallita
            }
        }

        return result;
    }
}
