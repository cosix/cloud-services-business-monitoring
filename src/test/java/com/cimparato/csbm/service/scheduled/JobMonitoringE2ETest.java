package com.cimparato.csbm.service.scheduled;

import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.domain.model.JobExecution;
import com.cimparato.csbm.repository.CloudServiceRepository;
import com.cimparato.csbm.repository.FileUploadRepository;
import com.cimparato.csbm.repository.JobExecutionRepository;
import com.cimparato.csbm.repository.ProcessingErrorRepository;
import com.cimparato.csbm.config.security.SecurityUtils;
import org.junit.jupiter.api.*;
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
import java.util.UUID;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JobMonitoringE2ETest {

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

    @BeforeEach
    void setUp() {
        processingErrorRepository.deleteAll();
        cloudServiceRepository.deleteAll();
        jobExecutionRepository.deleteAll();
        fileUploadRepository.deleteAll();

        System.setProperty("app.file-processing.upload-dir", tempDir.toString());
        when(securityUtils.getAuthenticatedUsername()).thenReturn("testuser");
    }

    @Test
    @Order(1)
    @WithMockUser(username = "testuser", roles = {"data_uploader"})
    @DisplayName("Verifica il monitoraggio dello stato di un job")
    void testJobStatusMonitoring() throws Exception {

        // 1. Carica il file CSV di test
        Resource resource = new ClassPathResource("csv/valid_services.csv");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "valid_services.csv",
                MediaType.TEXT_PLAIN_VALUE,
                resource.getInputStream());

        // 2. Esegue la richiesta di upload e ottiene l'ID del job
        MvcResult uploadResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/files/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").exists())
                .andReturn();

        // 3. Estrae l'ID del job dalla risposta
        String responseContent = uploadResult.getResponse().getContentAsString();
        String jobId = responseContent.split("\"jobId\":\"")[1].split("\"")[0];
        assertNotNull(jobId);

        // 4. Verifica lo stato iniziale del job
        MvcResult statusResult = mockMvc.perform(MockMvcRequestBuilders.get("/v1/jobs/" + jobId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        // 4. Verifica che lo stato sia PENDING o PROCESSING
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/jobs/" + jobId)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value(anyOf(
                        is("PENDING"),
                        is("PROCESSING"),
                        is("COMPLETED")
                )));

        // 5. Attende che il job venga completato
        String finalJobId = jobId;
        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    Optional<JobExecution> job = jobExecutionRepository.findByJobId(finalJobId);
                    return job.isPresent() && job.get().getStatus() == JobStatus.COMPLETED;
                });

        // 6. Verifica lo stato finale del job
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/jobs/" + jobId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.endTime").exists());
    }

    @Test
    @Order(2)
    @WithMockUser(username = "testuser", roles = {"data_uploader"})
    @DisplayName("Verifica la gestione di un job inesistente")
    void testNonExistentJobMonitoring() throws Exception {

        // Tenta di ottenere lo stato di un job inesistente
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/jobs/non-existent-job-id")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Job with id non-existent-job-id not found"));
    }

    @Test
    @Order(3)
    @WithMockUser(username = "testuser", roles = {"data_uploader"})
    @DisplayName("Verifica l'elenco dei job associati ad un file per un utente")
    void testJobsForFileAndUser() throws Exception {

        // 1. Carica un file CSV di test con un nome univoco
        Resource resource = new ClassPathResource("csv/valid_services.csv");
        String uniqueFileName = "user_jobs_test_" + UUID.randomUUID() + ".csv";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                uniqueFileName,
                MediaType.TEXT_PLAIN_VALUE,
                resource.getInputStream());

        // 2. Esegue la richiesta di upload
        MvcResult uploadResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/files/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").exists())
                .andExpect(jsonPath("$.data.fileHash").exists())
                .andReturn();

        // 3. Estrae il jobId e fileHash
        String responseContent = uploadResult.getResponse().getContentAsString();
        String jobId = responseContent.split("\"jobId\":\"")[1].split("\"")[0];
        String fileHash = responseContent.split("\"fileHash\":\"")[1].split("\"")[0];

        // 4. Attende che il job sia visibile nel repository
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    Optional<JobExecution> job = jobExecutionRepository.findByJobId(jobId);
                    return job.isPresent();
                });

        // 5. Verifica l'elenco dei job associati al file
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/jobs/file/" + fileHash)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].jobId").value(jobId))
                .andExpect(jsonPath("$.data.content[0].fileHash").value(fileHash))
                .andExpect(jsonPath("$.data.content[0].filename").value(uniqueFileName));

        // 6. Verifica che il job sia stato creato dall'utente corretto
        Optional<JobExecution> job = jobExecutionRepository.findByJobId(jobId);
        assertTrue(job.isPresent(), "Il job dovrebbe essere presente nel repository");
        assertEquals("testuser", job.get().getCreatedBy(), "Il job dovrebbe essere stato creato dall'utente 'testuser'");
    }

    @Test
    @Order(4)
    @WithMockUser(username = "testuser", roles = {"data_uploader"})
    @DisplayName("Verifica i dettagli di un job completato")
    void testCompletedJobDetails() throws Exception {

        // 1. Carica il file CSV di test
        Resource resource = new ClassPathResource("csv/valid_services.csv");
        String uniqueFileName = "job_details_test_" + UUID.randomUUID() + ".csv";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                uniqueFileName,
                MediaType.TEXT_PLAIN_VALUE,
                resource.getInputStream());

        // 2. Esegue la richiesta di upload
        MvcResult uploadResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/files/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").exists())
                .andReturn();

        // 3. Estrae il jobId usando il metodo split() come prima
        String responseContent = uploadResult.getResponse().getContentAsString();
        String jobId = responseContent.split("\"jobId\":\"")[1].split("\"")[0];

        // 4. Attende che il job venga completato
        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    Optional<JobExecution> job = jobExecutionRepository.findByJobId(jobId);
                    return job.isPresent() && job.get().getStatus() == JobStatus.COMPLETED;
                });

        // 5. Verifica i dettagli del job completato
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/jobs/" + jobId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.startTime").exists())
                .andExpect(jsonPath("$.data.endTime").exists())
                .andExpect(jsonPath("$.data.totalRecords").isNumber())
                .andExpect(jsonPath("$.data.validRecords").isNumber())
                .andExpect(jsonPath("$.data.invalidRecords").isNumber())
                .andExpect(jsonPath("$.data.errorMessage").doesNotExist());
    }

}