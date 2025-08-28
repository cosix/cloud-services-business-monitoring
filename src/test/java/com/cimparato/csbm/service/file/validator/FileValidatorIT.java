package com.cimparato.csbm.service.file.validator;

import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.domain.file.ValidationResult;
import com.cimparato.csbm.service.file.validator.impl.CloudServiceCsvHeaderRuleFile;
import com.cimparato.csbm.service.file.validator.impl.FileExtensionRuleFile;
import com.cimparato.csbm.service.file.validator.impl.FileNotEmptyRuleFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@Import({
        FileValidator.class,
        FileNotEmptyRuleFile.class,
        FileExtensionRuleFile.class,
        CloudServiceCsvHeaderRuleFile.class,
        FileValidatorIT.TestConfig.class
})
public class FileValidatorIT {

    @Autowired
    private FileValidator fileValidator;

    @Autowired
    private AppProperties appProperties;

    @Test
    @DisplayName("Verifica che un file valido passi tutte le validazioni")
    void testValidFilePassesAllValidations() {

        // arrange
        String csvContent = "customer_id,service_type,activation_date,expiration_date,amount,status\n" +
                "CUST001,PEC,2023-01-01,2024-01-01,29.99,ACTIVE";

        MultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent.getBytes()
        );

        // act
        ValidationResult result = fileValidator.validate(file);

        // Assert
        assertTrue(result.isValid(), "Valid file should pass all validations");
        assertTrue(result.getErrors().isEmpty(), "Valid file should have no errors");
    }

    @Test
    @DisplayName("Verifica che un file vuoto non passi la validazione")
    void testEmptyFileFailsValidation() {

        // arrange
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                new byte[0]
        );

        // act
        ValidationResult result = fileValidator.validate(file);

        // assert
        assertFalse(result.isValid(), "Empty file should fail validation");
        assertEquals(1, result.getErrors().size(), "Should have one error");
        assertEquals("File cannot be empty", result.getErrors().get(0));
    }

    @Test
    @DisplayName("Verifica che un file con estensione non valida non passi la validazione")
    void testInvalidExtensionFailsValidation() {

        // arrange
        String csvContent = "customer_id,service_type,activation_date,expiration_date,amount,status\n" +
                "CUST001,PEC,2023-01-01,2024-01-01,29.99,ACTIVE";

        MultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                csvContent.getBytes()
        );

        // act
        ValidationResult result = fileValidator.validate(file);

        // assert
        assertFalse(result.isValid(), "File with invalid extension should fail validation");
        assertEquals(1, result.getErrors().size(), "Should have one error");
        assertTrue(result.getErrors().get(0).contains("Allowed file extensions"),
                "Error should mention allowed extensions");
    }

    @Test
    @DisplayName("Verifica che un file con intestazione non valida non passi la validazione")
    void testInvalidHeaderFailsValidation() {

        // arrange
        String csvContent = "client,service,start_date,end_date,price,state\n" +
                "CUST001,PEC,2023-01-01,2024-01-01,29.99,ACTIVE";

        MultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent.getBytes()
        );

        // act
        ValidationResult result = fileValidator.validate(file);

        // assert
        assertFalse(result.isValid(), "File with invalid header should fail validation");
        assertEquals(1, result.getErrors().size(), "Should have one error");
        assertTrue(result.getErrors().get(0).contains("Invalid column"),
                "Error should mention invalid column");
    }

    @Test
    @DisplayName("Verifica che le estensioni configurate siano utilizzate correttamente")
    void testConfiguredAllowedExtensions() {

        // arrange
        String[] configuredExtensions = appProperties.getFileProcessing().getAllowedExtensions();

        // assert
        assertNotNull(configuredExtensions, "Configured extensions should not be null");
        assertTrue(configuredExtensions.length > 0, "Should have at least one configured extension");

        // test con un file che ha un'estensione fra quelle configurate
        String csvContent = "customer_id,service_type,activation_date,expiration_date,amount,status\n" +
                "CUST001,PEC,2023-01-01,2024-01-01,29.99,ACTIVE";

        MultipartFile file = new MockMultipartFile(
                "file",
                "test." + configuredExtensions[0],
                "text/plain",
                csvContent.getBytes()
        );

        // act - valida solo fileExtensionRule
        ValidationResult result = fileValidator.validate(file);

        // assert
        // se il file ha l'estensione configurata ma fallisce per altri motivi (es. header),
        // significa che l'estensione è stata accettata
        if (!result.isValid()) {
            assertFalse(result.getErrors().get(0).contains("Allowed file extensions"),
                    "Error should not be about file extension");
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public AppProperties appProperties() {
            AppProperties props = new AppProperties();
            AppProperties.FileProcessing fileProcessing = new AppProperties.FileProcessing();
            fileProcessing.setAllowedExtensions(new String[]{"csv"});
            props.setFileProcessing(fileProcessing);
            return props;
        }
    }
}
