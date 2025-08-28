package com.cimparato.csbm.service.file.validator;

import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.service.file.validator.impl.FileExtensionRuleFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FileExtensionRuleFileTest {

    @Mock
    private AppProperties appProperties;

    @Mock
    private AppProperties.FileProcessing fileProcessing;

    private FileExtensionRuleFile rule;

    @BeforeEach
    void setUp() {
        when(appProperties.getFileProcessing()).thenReturn(fileProcessing);
        when(fileProcessing.getAllowedExtensions()).thenReturn(new String[]{"csv"});

        rule = new FileExtensionRuleFile(appProperties);
        rule.init();
    }

    @Test
    @DisplayName("Verifica che un file con estensione consentita venga accettato")
    void allowedExtensionShouldBeAccepted() {

        // arrange
        MultipartFile csvFile = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                "content".getBytes()
        );

        // act
        boolean result = rule.validate(csvFile);

        // assert
        assertTrue(result, "File with allowed extension should be accepted");
    }

    @Test
    @DisplayName("Verifica che un file con estensione non consentita venga rifiutato")
    void disallowedExtensionShouldBeRejected() {

        // arrange
        MultipartFile txtFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "content".getBytes()
        );

        MultipartFile xlsxFile = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "content".getBytes()
        );

        // act & assert
        assertFalse(rule.validate(txtFile), "TXT file should be rejected");
        assertFalse(rule.validate(xlsxFile), "XLSX file should be rejected");
        assertEquals("Allowed file extensions: csv", rule.getErrorMessage());
    }

    @Test
    @DisplayName("Verifica che un file senza estensione venga rifiutato")
    void fileWithoutExtensionShouldBeRejected() {

        // arrange
        MultipartFile noExtensionFile = new MockMultipartFile(
                "file",
                "testfile",
                "application/octet-stream",
                "content".getBytes()
        );

        // act
        boolean result = rule.validate(noExtensionFile);

        // assert
        assertFalse(result, "File without extension should be rejected");
    }

    @Test
    @DisplayName("Verifica che la regola utilizzi le estensioni configurate")
    void ruleShouldUseConfiguredExtensions() {

        // arrange
        when(fileProcessing.getAllowedExtensions()).thenReturn(new String[]{"txt", "csv"});
        rule.init(); // reinizializza con le nuove estensioni

        MultipartFile txtFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "content".getBytes()
        );

        // act
        boolean result = rule.validate(txtFile);

        // assert
        assertTrue(result, "TXT file should be accepted when configured");
        assertEquals("Allowed file extensions: txt, csv", rule.getErrorMessage());
    }
}
