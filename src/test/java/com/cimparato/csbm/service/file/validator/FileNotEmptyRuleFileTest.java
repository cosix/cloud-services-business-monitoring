package com.cimparato.csbm.service.file.validator;

import com.cimparato.csbm.service.file.validator.impl.FileNotEmptyRuleFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;

public class FileNotEmptyRuleFileTest {

    private final FileNotEmptyRuleFile rule = new FileNotEmptyRuleFile();

    @Test
    @DisplayName("Verifica che un file vuoto venga rifiutato")
    void emptyFileShouldBeRejected() {
        // arrange
        MultipartFile emptyFile = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                new byte[0]
        );

        // act
        boolean result = rule.validate(emptyFile);

        // assert
        assertFalse(result, "Empty file should be rejected");
        assertEquals("File cannot be empty", rule.getErrorMessage());
    }

    @Test
    @DisplayName("Verifica che un file null venga rifiutato")
    void nullFileShouldBeRejected() {
        // act
        boolean result = rule.validate(null);

        // assert
        assertFalse(result, "Null file should be rejected");
        assertEquals("File cannot be empty", rule.getErrorMessage());
    }

    @Test
    @DisplayName("Verifica che un file non vuoto venga accettato")
    void nonEmptyFileShouldBeAccepted() {
        // arrange
        MultipartFile nonEmptyFile = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                "content".getBytes()
        );

        // act
        boolean result = rule.validate(nonEmptyFile);

        // assert
        assertTrue(result, "Non-empty file should be accepted");
    }
}
