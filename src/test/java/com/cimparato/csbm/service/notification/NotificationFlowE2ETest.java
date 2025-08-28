package com.cimparato.csbm.service.notification;

import com.cimparato.csbm.config.TestEmailConfig;
import com.cimparato.csbm.domain.enumeration.CloudServiceStatus;
import com.cimparato.csbm.domain.enumeration.CloudServiceType;
import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.domain.event.FileProcessingCompletedEvent;
import com.cimparato.csbm.domain.file.FileUploadStatus;
import com.cimparato.csbm.domain.model.CloudService;
import com.cimparato.csbm.domain.model.FileUpload;
import com.cimparato.csbm.domain.model.JobExecution;
import com.cimparato.csbm.domain.notification.NotificationType;
import com.cimparato.csbm.dto.fileupload.FileUploadJobDTO;
import com.cimparato.csbm.repository.CloudServiceRepository;
import com.cimparato.csbm.repository.FileUploadRepository;
import com.cimparato.csbm.repository.JobExecutionRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = {"test-notifications", "test-alerts.customer_expired"})
@Import(TestEmailConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NotificationFlowE2ETest {

    @Value("${app.notification.rule.active-service-older-than-notification-rule.email.recipient}")
    private String marketingEmailRecipient;

    @TempDir
    static Path tempDir;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private NotificationManager notificationManager;

    @Autowired
    private CloudServiceRepository cloudServiceRepository;

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private JobExecutionRepository jobExecutionRepository;

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {

        // configura un consumer Kafka per verificare i messaggi inviati
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker);

        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer());

        consumer = cf.createConsumer();
        consumer.subscribe(Arrays.asList("test-notifications", "test-alerts.customer_expired"));

        // pulisce i repository prima di ogni test
        cloudServiceRepository.deleteAll();
        jobExecutionRepository.deleteAll();
        fileUploadRepository.deleteAll();

        // configura la directory di upload per i test
        System.setProperty("app.file-processing.upload-dir", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    @Order(1)
    @DisplayName("Verifica il flusso di notifica per servizi attivi da più di 3 anni")
    void testActiveServiceOlderThanNotificationFlow() {

        // 1. Prepara i dati di test
        prepareOldActiveServicesTestData();

        // 2. Simula l'evento di completamento dell'elaborazione del file
        FileUpload fileUpload = fileUploadRepository.findAll().get(0);
        JobExecution jobExecution = jobExecutionRepository.findAll().get(0);

        FileUploadJobDTO fileUploadJobDTO = FileUploadJobDTO.builder()
                .fileHash(fileUpload.getFileHash())
                .filename(fileUpload.getFilename())
                .jobId(jobExecution.getJobId())
                .jobStatus(JobStatus.COMPLETED)
                .build();
        FileProcessingCompletedEvent event = new FileProcessingCompletedEvent(fileUploadJobDTO);

        // 3. Avvia il processo di notifica
        notificationManager.notificationEventListener(event);

        // 4. Verifica che i messaggi siano stati inviati a Kafka
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofMillis(10000));
        assertFalse(records.isEmpty(), "Dovrebbero esserci messaggi in Kafka");

        // 5. Verifica che i messaggi contengano le informazioni corrette
        var exists = false;
        for (var record: records) {
            if(record.value().contains(NotificationType.EMAIL.name()) ||
                    record.value().contains(marketingEmailRecipient) ||
                    record.value().contains("active since")) {
                exists = true;
            }
        }

        assertTrue(exists, "Il messaggio dovrebbe essere una notifica email per il marketing");

    }

    @Test
    @Order(2)
    @DisplayName("Verifica il flusso completo dall'upload del file alle notifiche")
    void testFileUploadToNotificationFlow() {

        // 1. Prepara i dati di test
        prepareTestData();

        // 2. Simula l'evento di completamento dell'elaborazione del file
        FileUpload fileUpload = fileUploadRepository.findAll().get(0);
        JobExecution jobExecution = jobExecutionRepository.findAll().get(0);

        FileUploadJobDTO fileUploadJobDTO = FileUploadJobDTO.builder()
                .fileHash(fileUpload.getFileHash())
                .filename(fileUpload.getFilename())
                .jobId(jobExecution.getJobId())
                .jobStatus(JobStatus.COMPLETED)
                .build();
        FileProcessingCompletedEvent event = new FileProcessingCompletedEvent(fileUploadJobDTO);

        // 3. Avvia il processo di notifica
        notificationManager.notificationEventListener(event);

        // 4. Verifica che i messaggi siano stati inviati a Kafka
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofMillis(10000), 2);
        assertFalse(records.isEmpty(), "Dovrebbero esserci messaggi in Kafka");

        // 5. Verifica che i messaggi contengano le informazioni corrette
        boolean foundExpiredNotification = false;
        boolean foundOldActiveNotification = false;

        for (var record : records) {
            String value = record.value();
            if (value.contains(CloudServiceStatus.EXPIRED.name())) {
                foundExpiredNotification = true;
            }
            if (value.contains("active since") || value.contains("upselling")) {
                foundOldActiveNotification = true;
            }
        }

        assertTrue(foundExpiredNotification || foundOldActiveNotification,
                "Dovrebbe essere presente almeno un tipo di notifica");
    }

    @Test
    @Order(3)
    @DisplayName("Verifica il flusso di notifica per servizi scaduti")
    void testExpiredServicesNotificationFlow() {

        // 1. Prepara i dati di test
        prepareExpiredServicesTestData();

        // 2. Simula l'evento di completamento dell'elaborazione del file
        FileUpload fileUpload = fileUploadRepository.findAll().get(0);
        JobExecution jobExecution = jobExecutionRepository.findAll().get(0);

        FileUploadJobDTO fileUploadJobDTO = FileUploadJobDTO.builder()
                .fileHash(fileUpload.getFileHash())
                .filename(fileUpload.getFilename())
                .jobId(jobExecution.getJobId())
                .jobStatus(JobStatus.COMPLETED)
                .build();
        FileProcessingCompletedEvent event = new FileProcessingCompletedEvent(fileUploadJobDTO);

        // 3. Avvia il processo di notifica
        notificationManager.notificationEventListener(event);

        // 4. Verifica che i messaggi siano stati inviati a Kafka
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofMillis(10000));
        assertFalse(records.isEmpty(), "Dovrebbero esserci messaggi in Kafka");

        // 5. Verifica che i messaggi contengano le informazioni corrette
        records.forEach(record -> assertTrue(record.value().contains("CUST004"),
                "Il messaggio dovrebbe contenere l'ID del cliente"));
    }

    private void prepareTestData() {

        FileUpload fileUpload = FileUpload.builder()
                .filename("test_data.csv")
                .fileHash("test-hash-data")
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

        // servizi scaduti
        CloudService service1 = new CloudService();
        service1.setCustomerId("CUST004");
        service1.setServiceType(CloudServiceType.HOSTING);
        service1.setActivationDate(LocalDate.now().minusYears(2));
        service1.setExpirationDate(LocalDate.now().minusMonths(1));
        service1.setAmount(BigDecimal.valueOf(120.50));
        service1.setStatus(CloudServiceStatus.EXPIRED);
        service1.setLastUpdated(LocalDateTime.now());
        services.add(service1);

        CloudService service2 = new CloudService();
        service2.setCustomerId("CUST004");
        service2.setServiceType(CloudServiceType.FIRMA_DIGITALE);
        service2.setActivationDate(LocalDate.now().minusMonths(18));
        service2.setExpirationDate(LocalDate.now().minusMonths(2));
        service2.setAmount(BigDecimal.valueOf(45.00));
        service2.setStatus(CloudServiceStatus.EXPIRED);
        service2.setLastUpdated(LocalDateTime.now());
        services.add(service2);

        // servizi attivi da più di 3 anni
        CloudService service3 = new CloudService();
        service3.setCustomerId("CUST001");
        service3.setServiceType(CloudServiceType.PEC);
        service3.setActivationDate(LocalDate.now().minusYears(4));
        service3.setExpirationDate(LocalDate.now().plusYears(1));
        service3.setAmount(BigDecimal.valueOf(29.99));
        service3.setStatus(CloudServiceStatus.ACTIVE);
        service3.setLastUpdated(LocalDateTime.now());
        services.add(service3);

        cloudServiceRepository.saveAll(services);
    }

    private void prepareExpiredServicesTestData() {

        FileUpload fileUpload = FileUpload.builder()
                .filename("test_expired_services.csv")
                .fileHash("test-hash-expired")
                .uploadDate(LocalDateTime.now())
                .uploadedBy("testuser")
                .status(FileUploadStatus.COMPLETED)
                .totalRecords(6)
                .validRecords(6)
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

        // Crea servizi scaduti per CUST004
        List<CloudService> expiredServices = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            CloudService service = new CloudService();
            service.setCustomerId("CUST004");
            service.setServiceType(CloudServiceType.values()[i % CloudServiceType.values().length]);
            service.setActivationDate(LocalDate.now().minusYears(2));
            service.setExpirationDate(LocalDate.now().minusMonths(i + 1));
            service.setAmount(BigDecimal.valueOf(29.99 + i * 10));
            service.setStatus(CloudServiceStatus.EXPIRED);
            service.setLastUpdated(LocalDateTime.now());
            expiredServices.add(service);
        }

        cloudServiceRepository.saveAll(expiredServices);
    }

    private void prepareOldActiveServicesTestData() {

        FileUpload fileUpload = FileUpload.builder()
                .filename("test_old_active_services.csv")
                .fileHash("test-hash-old-active")
                .uploadDate(LocalDateTime.now())
                .uploadedBy("testuser")
                .status(FileUploadStatus.COMPLETED)
                .totalRecords(2)
                .validRecords(2)
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

        // crea servizi attivi da più di 3 anni
        List<CloudService> oldActiveServices = new ArrayList<>();

        CloudService service1 = new CloudService();
        service1.setCustomerId("CUST001");
        service1.setServiceType(CloudServiceType.PEC);
        service1.setActivationDate(LocalDate.now().minusYears(4));
        service1.setExpirationDate(LocalDate.now().plusYears(1));
        service1.setAmount(BigDecimal.valueOf(29.99));
        service1.setStatus(CloudServiceStatus.ACTIVE);
        service1.setLastUpdated(LocalDateTime.now());
        oldActiveServices.add(service1);

        CloudService service2 = new CloudService();
        service2.setCustomerId("CUST003");
        service2.setServiceType(CloudServiceType.FATTURAZIONE);
        service2.setActivationDate(LocalDate.now().minusYears(5));
        service2.setExpirationDate(LocalDate.now().plusYears(2));
        service2.setAmount(BigDecimal.valueOf(79.90));
        service2.setStatus(CloudServiceStatus.ACTIVE);
        service2.setLastUpdated(LocalDateTime.now());
        oldActiveServices.add(service2);

        cloudServiceRepository.saveAll(oldActiveServices);
    }
}