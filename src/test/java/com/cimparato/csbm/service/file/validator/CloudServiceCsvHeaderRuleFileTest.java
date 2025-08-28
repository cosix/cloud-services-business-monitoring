package com.cimparato.csbm.service.file.validator;

import com.cimparato.csbm.service.file.validator.impl.CloudServiceCsvHeaderRuleFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CloudServiceCsvHeaderRuleFileTest {

    private final CloudServiceCsvHeaderRuleFile rule = new CloudServiceCsvHeaderRuleFile();

    @Test
    @DisplayName("Verifica che un file con intestazione corretta venga accettato")
    void correctHeaderShouldBeAccepted() {

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
        boolean result = rule.validate(file);

        // assert
        assertTrue(result, "File with correct header should be accepted");
    }

    @Test
    @DisplayName("Verifica che un file senza intestazione venga rifiutato")
    void fileWithoutHeaderShouldBeRejected() {

        // arrange
        String csvContent = "CUST001,PEC,2023-01-01,2024-01-01,29.99,ACTIVE";

        MultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent.getBytes()
        );

        // act
        boolean result = rule.validate(file);

        // assert
        assertFalse(result, "File without header should be rejected");
        assertTrue(rule.getErrorMessage().contains("Invalid column"),
                "Error message should indicate invalid column");
    }

    @Test
    @DisplayName("Verifica che un file con intestazione incompleta venga rifiutato")
    void fileWithIncompleteHeaderShouldBeRejected() {

        // arrange
        String csvContent = "customer_id,service_type,activation_date,expiration_date,amount\n" +
                "CUST001,PEC,2023-01-01,2024-01-01,29.99";

        MultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent.getBytes()
        );

        // act
        boolean result = rule.validate(file);

        // assert
        assertFalse(result, "File with incomplete header should be rejected");
        assertTrue(rule.getErrorMessage().contains("Invalid CSV header length"),
                "Error message should indicate invalid header length");
    }

    @Test
    @DisplayName("Verifica che un file con intestazione in ordine diverso venga rifiutato")
    void fileWithDifferentOrderHeaderShouldBeRejected() {

        // arrange
        String csvContent = "service_type,customer_id,activation_date,expiration_date,amount,status\n" +
                "PEC,CUST001,2023-01-01,2024-01-01,29.99,ACTIVE";

        MultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent.getBytes()
        );

        // act
        boolean result = rule.validate(file);

        // assert
        assertFalse(result, "File with different order header should be rejected");
        assertTrue(rule.getErrorMessage().contains("Invalid column"),
                "Error message should indicate invalid column");
    }

    @Test
    @DisplayName("Verifica che un file con nomi di intestazione leggermente diversi venga accettato")
    void fileWithSlightlyDifferentHeaderNamesShouldBeAccepted() {

        // arrange
        String csvContent = "Customer_Id,Service_Type,Activation_Date,Expiration_Date,Amount,Status\n" +
                "CUST001,PEC,2023-01-01,2024-01-01,29.99,ACTIVE";

        MultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent.getBytes()
        );

        // act
        boolean result = rule.validate(file);

        // assert
        assertTrue(result, "File with slightly different header names should be accepted");
    }

    @Test
    @DisplayName("Verifica che un file con nomi di intestazione completamente diversi venga rifiutato")
    void fileWithCompletelyDifferentHeaderNamesShouldBeRejected() {

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
        boolean result = rule.validate(file);

        // assert
        assertFalse(result, "File with completely different header names should be rejected");
        assertTrue(rule.getErrorMessage().contains("Invalid column"),
                "Error message should indicate invalid column");
    }
}
