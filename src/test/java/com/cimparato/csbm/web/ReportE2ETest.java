package com.cimparato.csbm.web;

import com.cimparato.csbm.domain.enumeration.CloudServiceStatus;
import com.cimparato.csbm.domain.enumeration.CloudServiceType;
import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.domain.file.FileUploadStatus;
import com.cimparato.csbm.domain.model.CloudService;
import com.cimparato.csbm.domain.model.FileUpload;
import com.cimparato.csbm.domain.model.JobExecution;
import com.cimparato.csbm.dto.report.SummaryReportDTO;
import com.cimparato.csbm.repository.CloudServiceRepository;
import com.cimparato.csbm.repository.FileUploadRepository;
import com.cimparato.csbm.repository.JobExecutionRepository;
import com.cimparato.csbm.service.report.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = {"test-notifications", "test-alerts.customer_expired"})
public class ReportE2ETest {

    @TempDir
    static Path tempDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CloudServiceRepository cloudServiceRepository;

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private JobExecutionRepository jobExecutionRepository;

    @Autowired
    private ReportService reportService;

    @BeforeEach
    void setUp() {

        // pulisce i repository prima di ogni test
        cloudServiceRepository.deleteAll();
        jobExecutionRepository.deleteAll();
        fileUploadRepository.deleteAll();

        // configura la directory di upload per i test
        System.setProperty("app.file-processing.upload-dir", tempDir.toString());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"data_analyst"})
    @DisplayName("Verifica che il report includa correttamente i dati dei servizi")
    void testReportIncludesCorrectData() throws Exception {

        // 1. Prepara i dati di test
        prepareTestData();

        // 2. Verifica la generazione del report JSON
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/v1/report/summary")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activeServicesByType.PEC").value(2))
                .andExpect(jsonPath("$.data.activeServicesByType.HOSTING").value(1))
                .andExpect(jsonPath("$.data.averageSpendingPerCustomer.CUST001").exists())
                .andExpect(jsonPath("$.data.averageSpendingPerCustomer.CUST002").exists())
                .andExpect(jsonPath("$.data.customersWithMultipleExpiredServices", hasItem("CUST004")))
                .andReturn();

        // 3. Verifica che il report PDF venga generato correttamente
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/report/summary/pdf")
                        .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"data_analyst"})
    @DisplayName("Verifica che il report gestisca correttamente il caso di nessun dato disponibile")
    void testReportHandlesNoDataAvailable() throws Exception {
        // 1. Verifica che non ci siano dati nel database
        assertEquals(0, cloudServiceRepository.count());

        // 2. Verifica la generazione del report JSON
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/report/summary")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activeServicesByType").isEmpty())
                .andExpect(jsonPath("$.data.averageSpendingPerCustomer").isEmpty())
                .andExpect(jsonPath("$.data.customersWithMultipleExpiredServices").isEmpty())
                .andExpect(jsonPath("$.data.customersWithServicesExpiringInNext15Days").isEmpty());

        // 3. Verifica che il report PDF venga generato anche senza dati
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/report/summary/pdf")
                        .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"data_analyst"})
    @DisplayName("Verifica che il report includa correttamente i servizi in scadenza nei prossimi 15 giorni")
    void testReportIncludesServicesExpiringInNext15Days() throws Exception {
        // 1. Prepara i dati di test con servizi in scadenza
        prepareExpiringServicesTestData();

        // 2. Verifica la generazione del report JSON
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/report/summary")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customersWithServicesExpiringInNext15Days", hasItem("CUST002")));

        // 3. Verifica direttamente il servizio
        SummaryReportDTO report = reportService.generateSummaryReport();
        assertTrue(report.getCustomersWithServicesExpiringInNext15Days().contains("CUST002"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"data_analyst"})
    @DisplayName("Verifica che il report calcoli correttamente la spesa media per cliente")
    void testReportCalculatesAverageSpendCorrectly() throws Exception {

        // 1. Prepara i dati di test
        prepareTestData();

        // 2. Verifica direttamente il servizio
        SummaryReportDTO report = reportService.generateSummaryReport();

        // CUST001 ha 2 servizi: 29.99 (PEC) e 120.50 (HOSTING), media = 75.245
        BigDecimal avgSpendCust001 = report.getAverageSpendingPerCustomer().get("CUST001");
        assertNotNull(avgSpendCust001);
        assertTrue(avgSpendCust001.compareTo(BigDecimal.valueOf(75.0)) > 0 &&
                avgSpendCust001.compareTo(BigDecimal.valueOf(76.0)) < 0);

        // CUST002 ha 1 servizio: 29.99 (PEC), media = 29.99
        BigDecimal avgSpendCust002 = report.getAverageSpendingPerCustomer().get("CUST002");
        assertNotNull(avgSpendCust002);
        assertTrue(avgSpendCust002.compareTo(BigDecimal.valueOf(29.0)) > 0 &&
                avgSpendCust002.compareTo(BigDecimal.valueOf(30.0)) < 0);
    }


    private void prepareTestData() {


        FileUpload fileUpload = FileUpload.builder()
                .filename("test_report_data.csv")
                .fileHash("test-hash-report")
                .uploadDate(LocalDateTime.now())
                .uploadedBy("testuser")
                .status(FileUploadStatus.COMPLETED)
                .totalRecords(10)
                .validRecords(10)
                .invalidRecords(0)
                .build();
        fileUpload = fileUploadRepository.save(fileUpload);

        JobExecution jobExecution = JobExecution.builder()
                .jobId(UUID.randomUUID().toString())
                .status(JobStatus.COMPLETED)
                .startTime(LocalDateTime.now().minusMinutes(5))
                .endTime(LocalDateTime.now())
                .fileUpload(fileUpload)
                .createdBy("testuser")
                .build();
        jobExecutionRepository.save(jobExecution);

        List<CloudService> services = new ArrayList<>();

        // servizi attivi per CUST001
        CloudService service1 = new CloudService();
        service1.setCustomerId("CUST001");
        service1.setServiceType(CloudServiceType.PEC);
        service1.setActivationDate(LocalDate.now().minusMonths(6));
        service1.setExpirationDate(LocalDate.now().plusMonths(6));
        service1.setAmount(BigDecimal.valueOf(29.99));
        service1.setStatus(CloudServiceStatus.ACTIVE);
        service1.setLastUpdated(LocalDateTime.now());
        services.add(service1);

        CloudService service2 = new CloudService();
        service2.setCustomerId("CUST001");
        service2.setServiceType(CloudServiceType.HOSTING);
        service2.setActivationDate(LocalDate.now().minusMonths(3));
        service2.setExpirationDate(LocalDate.now().plusMonths(9));
        service2.setAmount(BigDecimal.valueOf(120.50));
        service2.setStatus(CloudServiceStatus.ACTIVE);
        service2.setLastUpdated(LocalDateTime.now());
        services.add(service2);

        // servizio attivo per CUST002
        CloudService service3 = new CloudService();
        service3.setCustomerId("CUST002");
        service3.setServiceType(CloudServiceType.PEC);
        service3.setActivationDate(LocalDate.now().minusMonths(2));
        service3.setExpirationDate(LocalDate.now().plusMonths(10));
        service3.setAmount(BigDecimal.valueOf(29.99));
        service3.setStatus(CloudServiceStatus.ACTIVE);
        service3.setLastUpdated(LocalDateTime.now());
        services.add(service3);

        // servizi scaduti per CUST004
        CloudService service4 = new CloudService();
        service4.setCustomerId("CUST004");
        service4.setServiceType(CloudServiceType.HOSTING);
        service4.setActivationDate(LocalDate.now().minusYears(1));
        service4.setExpirationDate(LocalDate.now().minusMonths(1));
        service4.setAmount(BigDecimal.valueOf(120.50));
        service4.setStatus(CloudServiceStatus.EXPIRED);
        service4.setLastUpdated(LocalDateTime.now());
        services.add(service4);

        CloudService service5 = new CloudService();
        service5.setCustomerId("CUST004");
        service5.setServiceType(CloudServiceType.FIRMA_DIGITALE);
        service5.setActivationDate(LocalDate.now().minusMonths(18));
        service5.setExpirationDate(LocalDate.now().minusMonths(2));
        service5.setAmount(BigDecimal.valueOf(45.00));
        service5.setStatus(CloudServiceStatus.EXPIRED);
        service5.setLastUpdated(LocalDateTime.now());
        services.add(service5);

        cloudServiceRepository.saveAll(services);
    }

    private void prepareExpiringServicesTestData() {


        FileUpload fileUpload = FileUpload.builder()
                .filename("test_expiring_services.csv")
                .fileHash("test-hash-expiring")
                .uploadDate(LocalDateTime.now())
                .uploadedBy("testuser")
                .status(FileUploadStatus.COMPLETED)
                .totalRecords(1)
                .validRecords(1)
                .invalidRecords(0)
                .build();
        fileUpload = fileUploadRepository.save(fileUpload);

        JobExecution jobExecution = JobExecution.builder()
                .jobId(UUID.randomUUID().toString())
                .status(JobStatus.COMPLETED)
                .startTime(LocalDateTime.now().minusMinutes(5))
                .endTime(LocalDateTime.now())
                .fileUpload(fileUpload)
                .createdBy("testuser")
                .build();
        jobExecutionRepository.save(jobExecution);

        // Crea servizio in scadenza nei prossimi 15 giorni
        CloudService service = new CloudService();
        service.setCustomerId("CUST002");
        service.setServiceType(CloudServiceType.PEC);
        service.setActivationDate(LocalDate.now().minusMonths(11));
        service.setExpirationDate(LocalDate.now().plusDays(10)); // In scadenza tra 10 giorni
        service.setAmount(BigDecimal.valueOf(29.99));
        service.setStatus(CloudServiceStatus.ACTIVE);
        service.setLastUpdated(LocalDateTime.now());

        cloudServiceRepository.save(service);
    }
}
