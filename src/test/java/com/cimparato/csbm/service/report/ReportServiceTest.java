package com.cimparato.csbm.service.report;

import com.cimparato.csbm.domain.enumeration.CloudServiceType;
import com.cimparato.csbm.repository.CloudServiceRepository;
import com.cimparato.csbm.service.CloudServiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private CloudServiceRepository cloudServiceRepository;

    @Mock
    private CloudServiceService cloudServiceService;

    @Mock
    private PdfGenerator pdfGenerator;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(cloudServiceRepository, cloudServiceService, pdfGenerator);
    }

    @Test
    @DisplayName("Verifica che il report includa correttamente il conteggio dei servizi attivi raggruppati per tipo")
    void testReportIncludesActiveServicesByType() {

        // arrange
        Map<String, Long> activeServicesByType = new LinkedHashMap<>();
        activeServicesByType.put(CloudServiceType.PEC.name(), 10L);
        activeServicesByType.put(CloudServiceType.HOSTING.name(), 5L);
        activeServicesByType.put(CloudServiceType.FATTURAZIONE.name(), 3L);

        when(cloudServiceService.getActiveServicesByType()).thenReturn(activeServicesByType);
        when(cloudServiceService.getAverageSpendPerCustomer()).thenReturn(new HashMap<>());
        when(cloudServiceService.getCustomersWithMultipleExpiredServices()).thenReturn(Collections.emptyList());
        when(cloudServiceRepository.findCustomersWithServicesExpiringBetween(any(), any())).thenReturn(Collections.emptyList());

        // act
        var report = reportService.generateSummaryReport();

        // assert
        assertNotNull(report);
        assertEquals(activeServicesByType, report.getActiveServicesByType());
        assertEquals(3, report.getActiveServicesByType().size());
        assertEquals(10L, report.getActiveServicesByType().get(CloudServiceType.PEC.name()));
        assertEquals(5L, report.getActiveServicesByType().get(CloudServiceType.HOSTING.name()));
        assertEquals(3L, report.getActiveServicesByType().get(CloudServiceType.FATTURAZIONE.name()));
    }

    @Test
    @DisplayName("Verifica che il report calcoli correttamente la spesa media per cliente")
    void testReportCalculatesAverageSpendPerCustomer() {

        // arrange
        Map<String, BigDecimal> averageSpend = new LinkedHashMap<>();
        averageSpend.put("CUST001", new BigDecimal("45.99"));
        averageSpend.put("CUST001", new BigDecimal("45.99"));
        averageSpend.put("CUST002", new BigDecimal("120.50"));
        averageSpend.put("CUST003", new BigDecimal("29.99"));

        when(cloudServiceService.getActiveServicesByType()).thenReturn(new HashMap<>());
        when(cloudServiceService.getAverageSpendPerCustomer()).thenReturn(averageSpend);
        when(cloudServiceService.getCustomersWithMultipleExpiredServices()).thenReturn(Collections.emptyList());
        when(cloudServiceRepository.findCustomersWithServicesExpiringBetween(any(), any())).thenReturn(Collections.emptyList());

        // act
        var report = reportService.generateSummaryReport();

        // assert
        assertNotNull(report);
        assertEquals(averageSpend, report.getAverageSpendingPerCustomer());
        assertEquals(3, report.getAverageSpendingPerCustomer().size());
        assertEquals(new BigDecimal("45.99"), report.getAverageSpendingPerCustomer().get("CUST001"));
        assertEquals(new BigDecimal("120.50"), report.getAverageSpendingPerCustomer().get("CUST002"));
        assertEquals(new BigDecimal("29.99"), report.getAverageSpendingPerCustomer().get("CUST003"));
    }

    @Test
    @DisplayName("Verifica che il report includa correttamente l'elenco dei clienti con più servizi scaduti")
    void testReportIncludesCustomersWithMultipleExpiredServices() {

        // arrange
        List<String> customersWithExpired = Arrays.asList("CUST001", "CUST004", "CUST007");

        when(cloudServiceService.getActiveServicesByType()).thenReturn(new HashMap<>());
        when(cloudServiceService.getAverageSpendPerCustomer()).thenReturn(new HashMap<>());
        when(cloudServiceService.getCustomersWithMultipleExpiredServices()).thenReturn(customersWithExpired);
        when(cloudServiceRepository.findCustomersWithServicesExpiringBetween(any(), any())).thenReturn(Collections.emptyList());

        // act
        var report = reportService.generateSummaryReport();

        // assert
        assertNotNull(report);
        assertEquals(customersWithExpired, report.getCustomersWithMultipleExpiredServices());
        assertEquals(3, report.getCustomersWithMultipleExpiredServices().size());
        assertTrue(report.getCustomersWithMultipleExpiredServices().contains("CUST001"));
        assertTrue(report.getCustomersWithMultipleExpiredServices().contains("CUST004"));
        assertTrue(report.getCustomersWithMultipleExpiredServices().contains("CUST007"));
    }

    @Test
    @DisplayName("Verifica che il report includa correttamente l'elenco dei clienti con servizi in scadenza nei prossimi 15 giorni")
    void testReportIncludesCustomersWithServicesExpiringIn15Days() {

        // arrange
        List<String> customersWithExpiring = Arrays.asList("CUST002", "CUST005", "CUST008");

        when(cloudServiceService.getActiveServicesByType()).thenReturn(new HashMap<>());
        when(cloudServiceService.getAverageSpendPerCustomer()).thenReturn(new HashMap<>());
        when(cloudServiceService.getCustomersWithMultipleExpiredServices()).thenReturn(Collections.emptyList());
        when(cloudServiceRepository.findCustomersWithServicesExpiringBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(customersWithExpiring);

        // act
        var report = reportService.generateSummaryReport();

        // assert
        assertNotNull(report);
        assertEquals(customersWithExpiring, report.getCustomersWithServicesExpiringInNext15Days());
        assertEquals(3, report.getCustomersWithServicesExpiringInNext15Days().size());
        assertTrue(report.getCustomersWithServicesExpiringInNext15Days().contains("CUST002"));
        assertTrue(report.getCustomersWithServicesExpiringInNext15Days().contains("CUST005"));
        assertTrue(report.getCustomersWithServicesExpiringInNext15Days().contains("CUST008"));

        // Verifica che le date siano corrette (oggi e oggi+15)
        verify(cloudServiceRepository).findCustomersWithServicesExpiringBetween(
                eq(LocalDate.now()),
                eq(LocalDate.now().plusDays(15))
        );
    }

    @Test
    @DisplayName("Verifica che il report gestisca correttamente il caso di nessun dato disponibile")
    void testReportHandlesNoDataAvailable() {

        // arrange
        when(cloudServiceService.getActiveServicesByType()).thenReturn(Collections.emptyMap());
        when(cloudServiceService.getAverageSpendPerCustomer()).thenReturn(Collections.emptyMap());
        when(cloudServiceService.getCustomersWithMultipleExpiredServices()).thenReturn(Collections.emptyList());
        when(cloudServiceRepository.findCustomersWithServicesExpiringBetween(any(), any())).thenReturn(Collections.emptyList());

        // act
        var report = reportService.generateSummaryReport();

        // assert
        assertNotNull(report);
        assertTrue(report.getActiveServicesByType().isEmpty());
        assertTrue(report.getAverageSpendingPerCustomer().isEmpty());
        assertTrue(report.getCustomersWithMultipleExpiredServices().isEmpty());
        assertTrue(report.getCustomersWithServicesExpiringInNext15Days().isEmpty());
    }

    @Test
    @DisplayName("Verifica che il metodo generateSummaryReportPdf generi correttamente il PDF")
    void testGeneratesSummaryReportPdf() {

        // arrange
        Map<String, Long> activeServicesByType = new LinkedHashMap<>();
        activeServicesByType.put(CloudServiceType.PEC.name(), 10L);

        Map<String, BigDecimal> averageSpend = new LinkedHashMap<>();
        averageSpend.put("CUST001", new BigDecimal("45.99"));

        List<String> customersWithExpired = Collections.singletonList("CUST001");
        List<String> customersWithExpiring = Collections.singletonList("CUST002");

        when(cloudServiceService.getActiveServicesByType()).thenReturn(activeServicesByType);
        when(cloudServiceService.getAverageSpendPerCustomer()).thenReturn(averageSpend);
        when(cloudServiceService.getCustomersWithMultipleExpiredServices()).thenReturn(customersWithExpired);
        when(cloudServiceRepository.findCustomersWithServicesExpiringBetween(any(), any())).thenReturn(customersWithExpiring);

        byte[] pdfBytes = "PDF content".getBytes();
        when(pdfGenerator.generatePdf(any())).thenReturn(pdfBytes);

        // act
        byte[] result = reportService.generateSummaryReportPdf();

        // assert
        assertNotNull(result);
        assertArrayEquals(pdfBytes, result);
        verify(pdfGenerator).generatePdf(any());
    }
}
