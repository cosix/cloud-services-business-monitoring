package com.cimparato.csbm.service.file.validator.impl;

import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.service.file.validator.FileValidationRule;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class FileExtensionRuleFile implements FileValidationRule {

    private Set<String> allowedExtensions;
    private String errorMessage;

    private final AppProperties appProperties;

    public FileExtensionRuleFile(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void init() {
        String[] extensions = appProperties.getFileProcessing().getAllowedExtensions();
        this.allowedExtensions = new HashSet<>(Arrays.asList(extensions));
        this.errorMessage = "Allowed file extensions: " + String.join(", ", extensions);
    }

    @Override
    public boolean validate(MultipartFile file) {
        if (file == null)
            return false;

        String filename = file.getOriginalFilename();
        if (filename == null)
            return false;

        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return allowedExtensions.contains(extension);
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public int getOrder() {
        return 100; // Eseguito dopo FileNotEmptyRule
    }

}
