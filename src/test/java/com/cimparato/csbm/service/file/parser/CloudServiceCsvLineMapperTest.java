package com.cimparato.csbm.service.file.parser;

import com.cimparato.csbm.domain.enumeration.CloudServiceStatus;
import com.cimparato.csbm.domain.enumeration.CloudServiceType;
import com.cimparato.csbm.dto.cloudservice.CloudServiceDTO;
import com.cimparato.csbm.service.file.parser.impl.CloudServiceCsvLineMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CloudServiceCsvLineMapperTest {

    private CloudServiceCsvLineMapper mapper;
    private LocalDate today;
    private LocalDate yesterday;
    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        mapper = new CloudServiceCsvLineMapper();
        today = LocalDate.now();
        yesterday = today.minusDays(1);
        tomorrow = today.plusDays(1);
    }

    @Test
    @DisplayName("Verifica che un record con customer_id null generi un errore di validazione")
    void testNullCustomerId() {

        // arrange
        String[] line = {"", "PEC", "2023-01-01", "2024-01-01", "29.99", "ACTIVE"};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapLine(line, 1));

        assertTrue(exception.getMessage().contains("customer_id cannot be empty"));
    }

    @Test
    @DisplayName("Verifica che un record con service_type nullo generi un errore di validazione")
    void testNullServiceType() {

        // arrange
        String[] line = {"CUST001", "", "2023-01-01", "2024-01-01", "29.99", "ACTIVE"};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapLine(line, 1));

        assertTrue(exception.getMessage().contains("service_type cannot be empty"));
    }

    @Test
    @DisplayName("Verifica che un record con activation_date nullo generi un errore di validazione")
    void testNullActivationDate() {

        // arrange
        String[] line = {"CUST001", "PEC", "", "2024-01-01", "29.99", "ACTIVE"};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapLine(line, 1));

        assertTrue(exception.getMessage().contains("activation_date cannot be empty"));
    }

    @Test
    @DisplayName("Verifica che un record con expiration_date nullo generi un errore di validazione")
    void testNullExpirationDate() {

        // arrange
        String[] line = {"CUST001", "PEC", "2023-01-01", "", "29.99", "ACTIVE"};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapLine(line, 1));

        assertTrue(exception.getMessage().contains("expiration_date cannot be empty"));
    }

    @Test
    @DisplayName("Verifica che un record con status nullo generi un errore di validazione")
    void testNullStatus() {

        // arrange
        String[] line = {"CUST001", "PEC", "2023-01-01", "2024-01-01", "29.99", ""};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapLine(line, 1));

        assertTrue(exception.getMessage().contains("status cannot be empty"));
    }

    @Test
    @DisplayName("Verifica che un record con service_type non valido generi un errore di validazione")
    void testInvalidServiceType() {

        // arrange
        String[] line = {"CUST001", "INVALID_SERVICE", "2023-01-01", tomorrow.toString(), "29.99", "ACTIVE"};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapLine(line, 1));

        assertTrue(exception.getMessage().contains("service_type"));
        assertTrue(exception.getMessage().contains("not allowed"));
    }

    @Test
    @DisplayName("Verifica che un record con amount negativo generi un errore di validazione")
    void testNegativeAmount() {

        // arrange
        String[] line = {"CUST001", "PEC", "2023-01-01", tomorrow.toString(), "-29.99", "ACTIVE"};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapLine(line, 1));

        assertTrue(exception.getMessage().contains("amount cannot be negative"));
    }

    @Test
    @DisplayName("Verifica che un record con amount zero generi un errore di validazione")
    void testZeroAmount() {

        // arrange
        String[] line = {"CUST001", "PEC", "2023-01-01", tomorrow.toString(), "0", "ACTIVE"};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapLine(line, 1));

        assertTrue(exception.getMessage().contains("amount cannot be zero"));
    }

    @Test
    @DisplayName("Verifica che un record con status non valido generi un errore di validazione")
    void testInvalidStatus() {

        // arrange
        String[] line = {"CUST001", "PEC", "2023-01-01", "2024-01-01", "29.99", "INVALID_STATUS"};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapLine(line, 1));

        assertTrue(exception.getMessage().contains("status"));
        assertTrue(exception.getMessage().contains("not allowed"));
    }

    @Test
    @DisplayName("Verifica che un record con expiration_date precedente a activation_date generi un errore")
    void testExpirationBeforeActivation() {

        // arrange
        String[] line = {"CUST001", "PEC", "2023-01-01", "2022-01-01", "29.99", "ACTIVE"};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapLine(line, 1));

        assertTrue(exception.getMessage().contains("expiration_date cannot be before activation_date"));
    }

    @Test
    @DisplayName("Verifica che un record con stato ACTIVE e expiration_date nel passato generi un errore")
    void testActiveServiceWithPastExpiration() {

        // arrange
        String[] line = {"CUST001", "PEC", "2022-01-01", yesterday.toString(), "29.99", "ACTIVE"};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapLine(line, 1));

        assertTrue(exception.getMessage().contains("expiration_date cannot be less than current day for a service with status ACTIVE"));
    }

    @Test
    @DisplayName("Verifica che un record con stato EXPIRED e expiration_date nel futuro generi un errore")
    void testExpiredServiceWithFutureExpiration() {

        // arrange
        String[] line = {"CUST001", "PEC", "2022-01-01", tomorrow.toString(), "29.99", "EXPIRED"};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapLine(line, 1));

        assertTrue(exception.getMessage().contains("expiration_date cannot be greater than current day for a service with status EXPIRED"));
    }

    @Test
    @DisplayName("Verifica che un record con stato PENDING_RENEWAL e expiration_date nel passato generi un errore")
    void testPendingRenewalWithPastExpiration() {

        // arrange
        String[] line = {"CUST001", "PEC", "2022-01-01", yesterday.toString(), "29.99", "PENDING_RENEWAL"};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            mapper.mapLine(line, 1);
        });

        assertTrue(exception.getMessage().contains("expiration_date cannot be less than current day for a service with status PENDING_RENEWAL"));
    }

    @Test
    @DisplayName("Verifica che un record con amount non numerico generi un errore di parsing")
    void testNonNumericAmount() {

        // arrange
        String[] line = {"CUST001", "PEC", "2023-01-01", tomorrow.toString(), "abc", "ACTIVE"};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            mapper.mapLine(line, 1);
        });

        assertTrue(exception.getMessage().contains("Invalid amount format"));
    }

    @Test
    @DisplayName("Verifica che un record con activation_date in formato non valido generi un errore di parsing")
    void testInvalidActivationDateFormat() {

        // arrange
        String[] line = {"CUST001", "PEC", "01/01/2023", tomorrow.toString(), "29.99", "ACTIVE"};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            mapper.mapLine(line, 1);
        });

        assertTrue(exception.getMessage().contains("Invalid activation_date format"));
    }

    @Test
    @DisplayName("Verifica che un record con expiration_date in formato non valido generi un errore di parsing")
    void testInvalidExpirationDateFormat() {

        // arrange
        String[] line = {"CUST001", "PEC", "2023-01-01", "01/01/2024", "29.99", "ACTIVE"};

        // act & assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            mapper.mapLine(line, 1);
        });

        assertTrue(exception.getMessage().contains("Invalid expiration_date format"));
    }

    @Test
    @DisplayName("Test che un record valido venga mappato correttamente")
    void testValidRecordMapping() {

        // arrange
        String[] line = {"CUST001", "PEC", "2023-01-01", tomorrow.toString(), "29.99", "ACTIVE"};
        int lineNumber = 1;

        // Act
        CloudServiceDTO result = mapper.mapLine(line, lineNumber);

        // Assert
        assertNotNull(result);
        assertEquals("CUST001", result.getCustomerId());
        assertEquals(CloudServiceType.PEC, result.getServiceType());
        assertEquals(LocalDate.of(2023, 1, 1), result.getActivationDate());
        assertEquals(tomorrow, result.getExpirationDate());
        assertEquals(new BigDecimal("29.99"), result.getAmount());
        assertEquals(CloudServiceStatus.ACTIVE, result.getStatus());
        assertEquals(lineNumber, result.getLineNumber());
    }

    @Test
    @DisplayName("Test che un record con spazi extra venga mappato correttamente")
    void testRecordWithExtraSpaces() {

        // arrange
        String[] line = {" CUST001 ", " PEC ", " 2023-01-01 ", " " + tomorrow.toString() + " ", " 29.99 ", " ACTIVE "};
        int lineNumber = 1;

        // Act
        CloudServiceDTO result = mapper.mapLine(line, lineNumber);

        // Assert
        assertNotNull(result);
        assertEquals("CUST001", result.getCustomerId());
        assertEquals(CloudServiceType.PEC, result.getServiceType());
        assertEquals(LocalDate.of(2023, 1, 1), result.getActivationDate());
        assertEquals(tomorrow, result.getExpirationDate());
        assertEquals(new BigDecimal("29.99"), result.getAmount());
        assertEquals(CloudServiceStatus.ACTIVE, result.getStatus());
    }
}
