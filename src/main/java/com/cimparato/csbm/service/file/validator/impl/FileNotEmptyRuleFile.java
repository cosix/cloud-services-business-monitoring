package com.cimparato.csbm.service.file.validator.impl;

import com.cimparato.csbm.service.file.validator.FileValidationRule;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileNotEmptyRuleFile implements FileValidationRule {

    @Override
    public boolean validate(MultipartFile file) {
        if (file == null)
            return false;

        return !file.isEmpty();
    }

    @Override
    public String getErrorMessage() {
        return "File cannot be empty";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // Eseguito per primo
    }
}
