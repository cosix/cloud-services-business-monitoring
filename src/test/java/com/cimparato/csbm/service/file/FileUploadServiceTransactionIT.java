package com.cimparato.csbm.service.file;

import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.domain.event.DomainEventPublisher;
import com.cimparato.csbm.domain.file.FileUploadStatus;
import com.cimparato.csbm.domain.file.ValidationResult;
import com.cimparato.csbm.domain.model.FileUpload;
import com.cimparato.csbm.domain.model.JobExecution;
import com.cimparato.csbm.dto.fileupload.FileUploadJobDTO;
import com.cimparato.csbm.mapper.FileUploadMapper;
import com.cimparato.csbm.repository.FileUploadRepository;
import com.cimparato.csbm.service.JobExecutionService;
import com.cimparato.csbm.service.file.storage.FileStorageService;
import com.cimparato.csbm.service.file.validator.FileValidator;
import com.cimparato.csbm.util.FileUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@ActiveProfiles("test")
@Import({FileUploadServiceTransactionIT.TestConfig.class})
class FileUploadServiceTransactionIT {

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private FileValidator fileValidator;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private DomainEventPublisher eventPublisher;

    private MultipartFile validFile;
    private String username = "testuser";

    @BeforeEach
    void setUp() {
        validFile = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                "test content".getBytes()
        );

        // reset mocks
        reset(fileValidator, fileStorageService, jobExecutionService, eventPublisher);

        // mock per validazione
        when(fileValidator.validate(any(MultipartFile.class)))
                .thenReturn(new ValidationResult());

        // mock per JobExecution
        JobExecution jobExecution = JobExecution.builder()
                .jobId(UUID.randomUUID().toString())
                .status(JobStatus.PENDING)
                .startTime(LocalDateTime.now())
                .build();
        when(jobExecutionService.createJob(any(FileUpload.class), anyString()))
                .thenReturn(jobExecution);

        // mock per FileStorage
        when(fileStorageService.storeFile(any(MultipartFile.class), anyString()))
                .thenReturn("stored_file_path");
    }

    @Test
    @DisplayName("Verifica che in caso di errore durante il processo di upload, le transazioni vengano rollback")
    @Transactional(propagation = Propagation.NEVER) // esegue il test fuori da una transazione
    void testTransactionRollbackOnError() throws IOException {

        // arrange
        when(fileStorageService.storeFile(any(MultipartFile.class), anyString()))
                .thenThrow(new RuntimeException("Storage error"));

        // calcola l'hash del file per verificare che non sia presente dopo il rollback
        String fileHash = FileUtil.calculateFileHash(validFile);

        // act
        try {
            fileUploadService.uploadFile(validFile, username);
            fail("Should throw exception");
        } catch (Exception e) {
            // Expected exception
        }

        // assert
        Optional<FileUpload> fileUpload = fileUploadRepository.findByFileHash(fileHash);
        assertFalse(fileUpload.isPresent(), "File upload should be rolled back");
    }

    @Test
    @DisplayName("Verifica che in caso di successo, le transazioni vengano commit correttamente")
    @Transactional(propagation = Propagation.NEVER) // esegue il test fuori da una transazione
    void testTransactionCommitOnSuccess() throws IOException {

        // arrange
        String fileHash = FileUtil.calculateFileHash(validFile);

        // act
        FileUploadJobDTO result = fileUploadService.uploadFile(validFile, username);

        // assert
        Optional<FileUpload> fileUpload = fileUploadRepository.findByFileHash(fileHash);
        assertTrue(fileUpload.isPresent(), "File upload should be committed");
        assertEquals(FileUploadStatus.PENDING, fileUpload.get().getStatus());
        assertEquals(username, fileUpload.get().getUploadedBy());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public AppProperties appProperties() {
            AppProperties props = new AppProperties();
            AppProperties.FileProcessing fileProcessing = new AppProperties.FileProcessing();
            fileProcessing.setAllowedExtensions(new String[]{"csv"});
            fileProcessing.setUploadDir("./upload-test");
            fileProcessing.setBatchSize(100);
            props.setFileProcessing(fileProcessing);
            return props;
        }

        @Bean
        public FileValidator fileValidator() {
            return mock(FileValidator.class);
        }

        @Bean
        public FileStorageService fileStorageService() {
            return mock(FileStorageService.class);
        }

        @Bean
        public JobExecutionService jobExecutionService() {
            return mock(JobExecutionService.class);
        }

        @Bean
        public FileUploadMapper fileUploadMapper() {
            return mock(FileUploadMapper.class);
        }

        @Bean
        public DomainEventPublisher eventPublisher() {
            return mock(DomainEventPublisher.class);
        }

        @Bean
        public FileUploadService fileUploadService(
                FileStorageService fileStorageService,
                JobExecutionService jobExecutionService,
                FileValidator fileValidator,
                FileUploadRepository fileUploadRepository,
                FileUploadMapper fileUploadMapper,
                DomainEventPublisher eventPublisher) {
            return new FileUploadService(
                    fileStorageService,
                    jobExecutionService,
                    fileValidator,
                    fileUploadRepository,
                    fileUploadMapper,
                    eventPublisher
            );
        }
    }
}
