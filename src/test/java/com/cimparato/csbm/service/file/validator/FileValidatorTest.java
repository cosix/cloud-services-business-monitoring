package com.cimparato.csbm.service.file.validator;

import com.cimparato.csbm.domain.file.ValidationResult;
import com.cimparato.csbm.service.file.validator.impl.CloudServiceCsvHeaderRuleFile;
import com.cimparato.csbm.service.file.validator.impl.FileExtensionRuleFile;
import com.cimparato.csbm.service.file.validator.impl.FileNotEmptyRuleFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileValidatorTest {

    @Mock
    private FileNotEmptyRuleFile notEmptyRule;

    @Mock
    private FileExtensionRuleFile extensionRule;

    @Mock
    private CloudServiceCsvHeaderRuleFile headerRule;

    private FileValidator fileValidator;
    private MultipartFile testFile;


    @BeforeEach
    void setUp() {

        // configura l'ordine delle regole
        when(notEmptyRule.getOrder()).thenReturn(1);
        when(extensionRule.getOrder()).thenReturn(2);
        when(headerRule.getOrder()).thenReturn(3);

        // crea il validatore con le regole mockate
        fileValidator = new FileValidator(Arrays.asList(headerRule, notEmptyRule, extensionRule));

        // crea un file di test
        testFile = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                "content".getBytes()
        );
    }

    @Test
    @DisplayName("Verifica che le regole di validazione vengano applicate nell'ordine corretto")
    void testRulesShouldBeAppliedInCorrectOrder() {

        // arrange
        when(notEmptyRule.validate(testFile)).thenReturn(true);
        when(extensionRule.validate(testFile)).thenReturn(true);
        when(headerRule.validate(testFile)).thenReturn(true);

        // act
        ValidationResult result = fileValidator.validate(testFile);

        // assert
        assertTrue(result.isValid(), "Validation should pass when all rules pass");

        // verifica l'ordine di esecuzione
        InOrder inOrder = inOrder(notEmptyRule, extensionRule, headerRule);
        inOrder.verify(notEmptyRule).validate(testFile);
        inOrder.verify(extensionRule).validate(testFile);
        inOrder.verify(headerRule).validate(testFile);
    }

    @Test
    @DisplayName("Verifica che il file validator ritorni tutti gli errori")
    void testValidatorShouldReturnAllErrors() {

        // arrange
        when(notEmptyRule.validate(testFile)).thenReturn(false);
        when(notEmptyRule.getErrorMessage()).thenReturn("File is empty");

        // act
        ValidationResult result = fileValidator.validate(testFile);

        // assert
        assertFalse(result.isValid(), "Validation should fail when any rule fails");
        assertEquals(1, result.getErrors().size(), "Should have one error");
        assertEquals("File is empty", result.getErrors().get(0));

        // Verifica che le regole successive non siano state chiamate
        verify(notEmptyRule).validate(testFile);
        verify(extensionRule, never()).validate(testFile);
        verify(headerRule, never()).validate(testFile);
    }

    @Test
    @DisplayName("Verifica che un file sia considerato valido solo se passa tutte le regole di validazione")
    void testValidatorShouldConsiderFileValidOnlyIfAllRulesPass() {

        // arrange - primo caso: tutte le regole passano
        when(notEmptyRule.validate(testFile)).thenReturn(true);
        when(extensionRule.validate(testFile)).thenReturn(true);
        when(headerRule.validate(testFile)).thenReturn(true);

        // act
        ValidationResult result1 = fileValidator.validate(testFile);

        // assert
        assertTrue(result1.isValid(), "Validation should pass when all rules pass");

        // reset
        reset(notEmptyRule, extensionRule, headerRule);

        // arrange - secondo caso: una regola fallisce
        when(notEmptyRule.validate(testFile)).thenReturn(true);
        when(extensionRule.validate(testFile)).thenReturn(true);
        when(headerRule.validate(testFile)).thenReturn(false);
        when(headerRule.getErrorMessage()).thenReturn("Invalid header");

        // act
        ValidationResult result2 = fileValidator.validate(testFile);

        // assert
        assertFalse(result2.isValid(), "Validation should fail when any rule fails");
        assertEquals(1, result2.getErrors().size(), "Should have one error");
        assertEquals("Invalid header", result2.getErrors().get(0));
    }

    @Test
    @DisplayName("Verifica che un file valido passi tutte le validazioni")
    void testValidFilePassesAllValidations() {

        // arrange
        when(notEmptyRule.validate(testFile)).thenReturn(true);
        when(extensionRule.validate(testFile)).thenReturn(true);
        when(headerRule.validate(testFile)).thenReturn(true);

        // act
        ValidationResult result = fileValidator.validate(testFile);

        // assert
        assertTrue(result.isValid(), "Un file valido dovrebbe passare tutte le validazioni");
        assertTrue(result.getErrors().isEmpty(), "Non dovrebbero esserci errori per un file valido");
    }

}
