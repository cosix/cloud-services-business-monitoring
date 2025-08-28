package com.cimparato.csbm.service.file.parser;

import com.cimparato.csbm.domain.enumeration.CloudServiceStatus;
import com.cimparato.csbm.domain.enumeration.CloudServiceType;
import com.cimparato.csbm.domain.model.FileUpload;
import com.cimparato.csbm.domain.model.ProcessingError;
import com.cimparato.csbm.dto.cloudservice.CloudServiceDTO;
import com.cimparato.csbm.repository.ProcessingErrorRepository;
import com.cimparato.csbm.service.file.parser.impl.CloudServiceCsvLineMapper;
import com.cimparato.csbm.service.file.parser.impl.CloudServiceCsvParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({FileParserStrategy.class, CloudServiceCsvParser.class, CloudServiceCsvLineMapper.class})
class FileParsingAndValidationIT {

    @Autowired
    private FileParserStrategy fileParserStrategy;

    @Autowired
    private ProcessingErrorRepository processingErrorRepository;

    private FileUpload testFileUpload;
    private List<CloudServiceDTO> processedRecords;
    private List<ProcessingError> processingErrors;

    private LocalDate today;
    private LocalDate yesterday;
    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {

        today = LocalDate.now();
        yesterday = today.minusDays(1);
        tomorrow = today.plusDays(1);

        testFileUpload = FileUpload.builder()
                .id(1L)
                .filename("test.csv")
                .fileHash("abc123")
                .uploadDate(LocalDateTime.now())
                .uploadedBy("testuser")
                .build();

        processedRecords = new ArrayList<>();
        processingErrors = new ArrayList<>();
    }

    @Test
    @DisplayName("Test parsing di un file CSV valido")
    void testParseValidCsvFile() throws Exception {

        // arrange
        String csvContent = "customer_id,service_type,activation_date,expiration_date,amount,status\n" +
                "CUST001,PEC,2023-01-01," + tomorrow + ",29.99,ACTIVE\n" +
                "CUST002,HOSTING,2023-02-15," + tomorrow + ",120.50,ACTIVE\n";

        MultipartFile file = new MockMultipartFile(
                "file",
                "valid.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        // act
        FileParser<CloudServiceDTO> parser = fileParserStrategy.getParser("csv", CloudServiceDTO.class);
        try (InputStream is = file.getInputStream()) {
            parser.parse(is, processedRecords::add);
        }

        // assert
        assertEquals(2, processedRecords.size(), "Dovrebbero essere processati tutti i record validi");
        assertEquals(0, parser.getParsingErrors().size(), "Non dovrebbero esserci errori di parsing");

        CloudServiceDTO record1 = processedRecords.get(0);
        assertEquals("CUST001", record1.getCustomerId());
        assertEquals(CloudServiceType.PEC, record1.getServiceType());
        assertEquals(LocalDate.of(2023, 1, 1), record1.getActivationDate());
        assertEquals(tomorrow, record1.getExpirationDate());
        assertEquals("29.99", record1.getAmount().toString());
        assertEquals(CloudServiceStatus.ACTIVE, record1.getStatus());

        CloudServiceDTO record2 = processedRecords.get(1);
        assertEquals("CUST002", record2.getCustomerId());
        assertEquals(CloudServiceType.HOSTING, record2.getServiceType());
    }

    @Test
    @DisplayName("Test parsing di un file CSV con errori")
    void testParseCsvFileWithErrors() throws Exception {

        // arrange
        String csvContent = "customer_id,service_type,activation_date,expiration_date,amount,status\n" +
                "CUST001,PEC,2023-01-01," + tomorrow + ",29.99,ACTIVE\n" +
                ",PEC,2023-01-01,2025-01-01,29.99,ACTIVE\n" + // customer_id mancante
                "CUST003,INVALID,2023-01-01,2025-01-01,29.99,ACTIVE\n" + // service_type non valido
                "CUST004,PEC,2023-01-01,2022-01-01,29.99,ACTIVE\n"; // expiration_date prima di activation_date

        MultipartFile file = new MockMultipartFile(
                "file",
                "invalid.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        // act
        FileParser<CloudServiceDTO> parser = fileParserStrategy.getParser("csv", CloudServiceDTO.class);
        try (InputStream is = file.getInputStream()) {
            parser.parse(is, processedRecords::add);
        }

        // assert
        assertEquals(1, processedRecords.size(), "Dovrebbe essere processato solo il record valido");
        assertEquals(3, parser.getParsingErrors().size(), "Dovrebbero esserci 3 errori di parsing");

        // verifica che il record valido sia stato processato correttamente
        CloudServiceDTO validRecord = processedRecords.get(0);
        assertEquals("CUST001", validRecord.getCustomerId());

        // verifica gli errori di parsing
        List<ParsingError> errors = parser.getParsingErrors();
        assertTrue(errors.stream().anyMatch(e -> e.getLineNumber() == 3 && e.getErrorMessage().contains("customer_id")));
        assertTrue(errors.stream().anyMatch(e -> e.getLineNumber() == 4 && e.getErrorMessage().contains("service_type")));
        assertTrue(errors.stream().anyMatch(e -> e.getLineNumber() == 5 && e.getErrorMessage().contains("expiration_date")));
    }

    @Test
    @DisplayName("Test parsing di un file CSV con formato di date non valido")
    void testParseCsvFileWithInvalidDateFormat() throws Exception {

        // arrange
        String csvContent = "customer_id,service_type,activation_date,expiration_date,amount,status\n" +
                "CUST001,PEC,01/01/2023,01/01/2025,29.99,ACTIVE\n"; // formato date non valido

        MultipartFile file = new MockMultipartFile(
                "file",
                "invalid_dates.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        // act
        FileParser<CloudServiceDTO> parser = fileParserStrategy.getParser("csv", CloudServiceDTO.class);
        try (InputStream is = file.getInputStream()) {
            parser.parse(is, processedRecords::add);
        }

        // assert
        assertEquals(0, processedRecords.size(), "Non dovrebbero esserci record processati");
        assertEquals(1, parser.getParsingErrors().size(), "Dovrebbe esserci un errore di parsing");

        ParsingError error = parser.getParsingErrors().get(0);
        assertEquals(2, error.getLineNumber());
        assertTrue(error.getErrorMessage().contains("Invalid activation_date format"));
    }

    @Test
    @DisplayName("Test parsing di un file CSV con amount non numerico")
    void testParseCsvFileWithNonNumericAmount() throws Exception {

        // arrange
        String csvContent = "customer_id,service_type,activation_date,expiration_date,amount,status\n" +
                "CUST001,PEC,2023-01-01," + tomorrow + ",abc,ACTIVE\n"; // amount non numerico

        MultipartFile file = new MockMultipartFile(
                "file",
                "invalid_amount.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        // act
        FileParser<CloudServiceDTO> parser = fileParserStrategy.getParser("csv", CloudServiceDTO.class);
        try (InputStream is = file.getInputStream()) {
            parser.parse(is, processedRecords::add);
        }

        // assert
        assertEquals(0, processedRecords.size(), "Non dovrebbero esserci record processati");
        assertEquals(1, parser.getParsingErrors().size(), "Dovrebbe esserci un errore di parsing");

        ParsingError error = parser.getParsingErrors().get(0);
        assertEquals(2, error.getLineNumber());
        assertTrue(error.getErrorMessage().contains("Invalid amount format"));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public FileParserStrategy fileParserStrategy(List<FileParser<?>> parsers) {
            return new FileParserStrategy(parsers);
        }
    }
}
