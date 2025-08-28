package com.cimparato.csbm.service.file.parser;

import com.cimparato.csbm.dto.cloudservice.CloudServiceDTO;
import com.cimparato.csbm.service.file.parser.impl.CloudServiceCsvParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudServiceCsvParserTest {

    @Mock
    private CsvLineMapper<CloudServiceDTO> lineMapper;

    private CloudServiceCsvParser parser;
    private List<CloudServiceDTO> processedRecords;

    @BeforeEach
    void setUp() {
        parser = new CloudServiceCsvParser(lineMapper);
        processedRecords = new ArrayList<>();
    }

    @Test
    @DisplayName("Verifica che il processo continui anche in presenza di record non validi")
    void testParsingContinuesWithInvalidRecords() {

        // arrange
        String csvContent = "customer_id,service_type,activation_date,expiration_date,amount,status\n" +
                "CUST001,PEC,2023-01-01,2024-01-01,29.99,ACTIVE\n" +
                "CUST002,INVALID,2023-01-01,2024-01-01,29.99,ACTIVE\n" +
                "CUST003,PEC,2023-01-01,2024-01-01,29.99,ACTIVE\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes());

        CloudServiceDTO validRecord1 = new CloudServiceDTO();
        validRecord1.setCustomerId("CUST001");

        CloudServiceDTO validRecord2 = new CloudServiceDTO();
        validRecord2.setCustomerId("CUST003");

        // Configura il mock per simulare un errore sul secondo record
        when(lineMapper.mapLine(any(String[].class), eq(2)))
                .thenReturn(validRecord1);
        when(lineMapper.mapLine(any(String[].class), eq(3)))
                .thenThrow(new IllegalArgumentException("Invalid service type"));
        when(lineMapper.mapLine(any(String[].class), eq(4)))
                .thenReturn(validRecord2);

        // act
        parser.parse(inputStream, processedRecords::add);

        // assert
        assertEquals(2, processedRecords.size(), "Dovrebbero essere processati solo i record validi");
        assertEquals("CUST001", processedRecords.get(0).getCustomerId());
        assertEquals("CUST003", processedRecords.get(1).getCustomerId());
        assertEquals(1, parser.getParsingErrors().size(), "Dovrebbe essere registrato un errore di parsing");
    }

    @Test
    @DisplayName("Verifica che gli errori di parsing vengano correttamente loggati")
    void testParsingErrorsAreLogged() {

        // arrange
        String csvContent = "customer_id,service_type,activation_date,expiration_date,amount,status\n" +
                "CUST001,PEC,2023-01-01,2024-01-01,29.99,ACTIVE\n" +
                "CUST002,INVALID,2023-01-01,2024-01-01,29.99,ACTIVE\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes());

        CloudServiceDTO validRecord = new CloudServiceDTO();
        validRecord.setCustomerId("CUST001");

        // mock per simulare un errore sul secondo record
        when(lineMapper.mapLine(any(String[].class), eq(2)))
                .thenReturn(validRecord);
        when(lineMapper.mapLine(any(String[].class), eq(3)))
                .thenThrow(new IllegalArgumentException("Invalid service type"));

        // act
        parser.parse(inputStream, processedRecords::add);

        // assert
        List<ParsingError> errors = parser.getParsingErrors();
        assertEquals(1, errors.size(), "Dovrebbe essere registrato un errore di parsing");
        assertEquals(3, errors.get(0).getLineNumber(), "L'errore dovrebbe essere associato alla riga 3");
        assertEquals("Invalid service type", errors.get(0).getErrorMessage());
        assertTrue(errors.get(0).getRawData().contains("CUST002"), "Il dato grezzo dovrebbe contenere il record problematico");
    }

    @Test
    @DisplayName("Verifica che il parser supporti correttamente l'estensione CSV")
    void testParserSupportsCSVExtension() {

        // act & Assert
        assertTrue(parser.supports("csv"), "Il parser dovrebbe supportare l'estensione csv");
        assertTrue(parser.supports("CSV"), "Il parser dovrebbe supportare l'estensione CSV (case insensitive)");
        assertFalse(parser.supports("txt"), "Il parser non dovrebbe supportare altre estensioni");
    }

}
