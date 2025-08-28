package com.cimparato.csbm.web;

import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.domain.model.FileUpload;
import com.cimparato.csbm.domain.model.JobExecution;
import com.cimparato.csbm.repository.CloudServiceRepository;
import com.cimparato.csbm.repository.FileUploadRepository;
import com.cimparato.csbm.repository.JobExecutionRepository;
import com.cimparato.csbm.repository.ProcessingErrorRepository;
import com.cimparato.csbm.config.security.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = {"test-notifications", "test-alerts.customer_expired"})
public class FileUploadE2ETest {

    @TempDir
    static Path tempDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private JobExecutionRepository jobExecutionRepository;

    @Autowired
    private CloudServiceRepository cloudServiceRepository;

    @Autowired
    private ProcessingErrorRepository processingErrorRepository;

    @MockBean
    private SecurityUtils securityUtils;


    @BeforeAll
    static void setupClass() {
        // configura la directory di upload per i test
        System.setProperty("app.file-processing.upload-dir", tempDir.toString());
    }

    @BeforeEach
    void setUp() {
        // pulisce i repository prima di ogni test
        processingErrorRepository.deleteAll();
        cloudServiceRepository.deleteAll();
        jobExecutionRepository.deleteAll();
        fileUploadRepository.deleteAll();

        when(securityUtils.getAuthenticatedUsername()).thenReturn("testuser");
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"data_uploader"})
    @DisplayName("Verifica il flusso completo di upload di un file CSV valido")
    void testValidFileUpload() throws Exception {

        // 1. Carica il file CSV di test
        Resource resource = new ClassPathResource("csv/valid_services.csv");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "valid_services.csv",
                MediaType.TEXT_PLAIN_VALUE,
                resource.getInputStream());

        // 2. Esegui la richiesta di upload
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/files/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").exists())
                .andExpect(jsonPath("$.data.fileHash").exists())
                .andReturn();

        // 3. Estrai l'ID del job e l'hash del file
        String responseContent = result.getResponse().getContentAsString();
        String jobId = responseContent.split("\"jobId\":\"")[1].split("\"")[0];
        String fileHash = responseContent.split("\"fileHash\":\"")[1].split("\"")[0];

        assertNotNull(jobId);
        assertNotNull(fileHash);

        // 4. Verifica che il file sia stato salvato nel database
        await().atMost(5, TimeUnit.SECONDS).until(() ->
                fileUploadRepository.findByFileHash(fileHash).isPresent());

        FileUpload fileUpload = fileUploadRepository.findByFileHash(fileHash).get();
        assertEquals("valid_services.csv", fileUpload.getFilename());
        assertEquals("testuser", fileUpload.getUploadedBy());

        // 5. Verifica che il job venga completato
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            Optional<JobExecution> job = jobExecutionRepository.findByJobId(jobId);
            return job.isPresent() && job.get().getStatus() == JobStatus.COMPLETED;
        });

        // 6. Verifica lo stato del job tramite l'API
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/jobs/" + jobId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.fileHash").value(fileHash))
                .andExpect(jsonPath("$.data.filename").value("valid_services.csv"))
                .andExpect(jsonPath("$.data.totalRecords").isNumber())
                .andExpect(jsonPath("$.data.validRecords").isNumber())
                .andExpect(jsonPath("$.data.invalidRecords").isNumber());

        // 7. Verifica le informazioni sul file tramite l'API
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/files/" + fileHash)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.filename").value("valid_services.csv"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.uploadedBy").value("testuser"));

        // 8. Verifica i job associati al file tramite l'API
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/jobs/file/" + fileHash)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.content[0].jobId").value(jobId))
                .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"data_uploader"})
    @DisplayName("Verifica la gestione di un file CSV non valido durante l'upload")
    void testInvalidFileUpload() throws Exception {

        // 1. Crea un file CSV con intestazione non valida
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invalid_header.csv",
                MediaType.TEXT_PLAIN_VALUE,
                "customer,service,start_date,end_date,price,state\nCUST001,PEC,2018-01-15,2026-11-15,29.99,ACTIVE".getBytes());

        // 2. Esegui la richiesta di upload
        mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/files/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"data_uploader"})
    @DisplayName("Verifica la gestione di un file non CSV durante l'upload")
    void testNonCsvFileUpload() throws Exception {

        // 1. Crea un file non CSV
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "This is not a CSV file".getBytes());

        // 2. Esegui la richiesta di upload
        mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/files/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Allowed file extensions")));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"data_uploader"})
    @DisplayName("Verifica la gestione di un file vuoto durante l'upload")
    void testEmptyFileUpload() throws Exception {

        // 1. Crea un file CSV vuoto
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.csv",
                MediaType.TEXT_PLAIN_VALUE,
                "".getBytes());

        // 2. Esegui la richiesta di upload
        mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/files/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("File cannot be empty")));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"data_uploader"})
    @DisplayName("Verifica la gestione di un file duplicato durante l'upload")
    void testDuplicateFileUpload() throws Exception {

        // 1. Carica il file CSV di test
        Resource resource = new ClassPathResource("csv/valid_services.csv");
        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "valid_services.csv",
                MediaType.TEXT_PLAIN_VALUE,
                resource.getInputStream());

        // 2. Esegui la prima richiesta di upload
        MvcResult result1 = mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/files/upload")
                        .file(file1))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent1 = result1.getResponse().getContentAsString();
        String jobId1 = responseContent1.split("\"jobId\":\"")[1].split("\"")[0];

        // 3. Attendi il completamento del primo job
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            Optional<JobExecution> job = jobExecutionRepository.findByJobId(jobId1);
            return job.isPresent() && job.get().getStatus() == JobStatus.COMPLETED;
        });

        // 4. Carica lo stesso file una seconda volta
        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "valid_services.csv",
                MediaType.TEXT_PLAIN_VALUE,
                resource.getInputStream());

        // 5. Esegui la seconda richiesta di upload
        mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/files/upload")
                        .file(file2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("File has already been uploaded and processed")));
    }
}

