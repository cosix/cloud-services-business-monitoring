package com.cimparato.csbm.service.report;

import com.cimparato.csbm.domain.enumeration.CloudServiceType;
import com.cimparato.csbm.dto.report.SummaryReportDTO;
import com.cimparato.csbm.service.report.impl.JasperReportGenerator;
import com.cimparato.csbm.web.rest.errors.ReportGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JasperReportGeneratorTest {

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Resource resource;

    private JasperReportGenerator reportGenerator;

    @BeforeEach
    void setUp() {
        reportGenerator = new JasperReportGenerator(resourceLoader, objectMapper);
    }

    @Test
    @DisplayName("Verifica che il generatore PDF crei correttamente un report PDF")
    void testGeneratesPdfReport() throws Exception {

        // arrange
        SummaryReportDTO summaryReport = createTestSummaryReport();

        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("<jasperReport></jasperReport>".getBytes()));

        JasperReport mockReport = mock(JasperReport.class);

        try (MockedStatic<JasperCompileManager> jasperCompileManager = mockStatic(JasperCompileManager.class);
             MockedStatic<JasperFillManager> jasperFillManager = mockStatic(JasperFillManager.class)) {

            jasperCompileManager.when(() -> JasperCompileManager.compileReport(any(InputStream.class)))
                    .thenReturn(mockReport);

            jasperFillManager.when(() -> JasperFillManager.fillReport(eq(mockReport), any(Map.class), any(JREmptyDataSource.class)))
                    .thenReturn(null);

            byte[] expectedBytes = "PDF content".getBytes();
            try (MockedStatic<net.sf.jasperreports.engine.JasperExportManager> jasperExportManager =
                         mockStatic(net.sf.jasperreports.engine.JasperExportManager.class)) {

                jasperExportManager.when(() -> net.sf.jasperreports.engine.JasperExportManager.exportReportToPdf(any()))
                        .thenReturn(expectedBytes);

                // act
                byte[] result = reportGenerator.generatePdf(summaryReport);

                // assert
                assertNotNull(result);
                assertArrayEquals(expectedBytes, result);
            }
        }
    }

    @Test
    @DisplayName("Verifica che il report generator gestisca correttamente gli errori di compilazione del template")
    void testHandlesTemplateCompilationErrors() throws Exception {

        // arrange
        SummaryReportDTO summaryReport = createTestSummaryReport();

        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("<invalid>".getBytes()));

        try (MockedStatic<JasperCompileManager> jasperCompileManager = mockStatic(JasperCompileManager.class)) {
            jasperCompileManager.when(() -> JasperCompileManager.compileReport(any(InputStream.class)))
                    .thenThrow(new net.sf.jasperreports.engine.JRException("Compilation error"));

            // act & assert
            assertThrows(ReportGenerationException.class, () -> reportGenerator.generatePdf(summaryReport));
        }
    }

    private SummaryReportDTO createTestSummaryReport() {
        SummaryReportDTO report = new SummaryReportDTO();

        Map<String, Long> activeServicesByType = new LinkedHashMap<>();
        activeServicesByType.put(CloudServiceType.PEC.name(), 10L);
        activeServicesByType.put(CloudServiceType.HOSTING.name(), 5L);
        report.setActiveServicesByType(activeServicesByType);

        Map<String, BigDecimal> averageSpend = new LinkedHashMap<>();
        averageSpend.put("CUST001", new BigDecimal("45.99"));
        averageSpend.put("CUST002", new BigDecimal("120.50"));
        report.setAverageSpendingPerCustomer(averageSpend);

        report.setCustomersWithMultipleExpiredServices(Arrays.asList("CUST001", "CUST004"));
        report.setCustomersWithServicesExpiringInNext15Days(Arrays.asList("CUST002", "CUST005"));

        return report;
    }
}
