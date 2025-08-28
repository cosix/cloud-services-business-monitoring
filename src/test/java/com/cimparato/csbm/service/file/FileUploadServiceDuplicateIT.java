package com.cimparato.csbm.service.file;

import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.domain.event.DomainEventPublisher;
import com.cimparato.csbm.domain.file.FileUploadStatus;
import com.cimparato.csbm.domain.file.ValidationResult;
import com.cimparato.csbm.domain.model.FileUpload;
import com.cimparato.csbm.domain.model.JobExecution;
import com.cimparato.csbm.mapper.FileUploadMapper;
import com.cimparato.csbm.repository.FileUploadRepository;
import com.cimparato.csbm.service.JobExecutionService;
import com.cimparato.csbm.service.file.storage.FileStorageService;
import com.cimparato.csbm.service.file.validator.FileValidator;
import com.cimparato.csbm.util.FileUtil;
import com.cimparato.csbm.web.rest.errors.DuplicateFileException;
import com.cimparato.csbm.web.rest.errors.FileProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@Import({FileUploadService.class})
class FileUploadServiceDuplicateIT {

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private JobExecutionService jobExecutionService;

    @MockBean
    private FileValidator fileValidator;

    @MockBean
    private FileUploadRepository fileUploadRepository;

    @MockBean
    private FileUploadMapper fileUploadMapper;

    @MockBean
    private DomainEventPublisher eventPublisher;

    @Autowired
    private FileUploadService fileUploadService;

    @Captor
    private ArgumentCaptor<FileUpload> fileUploadCaptor;

    private MultipartFile testFile;
    private String fileHash;
    private FileUpload existingFileUpload;
    private JobExecution jobExecution;
    private String username = "testuser";

    @BeforeEach
    void setUp() throws IOException {
        testFile = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                "test content".getBytes()
        );

        fileHash = FileUtil.calculateFileHash(testFile);

        jobExecution = JobExecution.builder()
                .jobId("job123")
                .status(JobStatus.COMPLETED)
                .startTime(LocalDateTime.now().minusDays(1))
                .endTime(LocalDateTime.now().minusDays(1).plusHours(1))
                .fileUpload(existingFileUpload)
                .build();

        existingFileUpload = FileUpload.builder()
                .id(1L)
                .filename("test.csv")
                .fileHash(fileHash)
                .uploadDate(LocalDateTime.now().minusDays(1))
                .uploadedBy("previoususer")
                .status(FileUploadStatus.COMPLETED)
                .jobExecutions(new HashSet<>())
                .build();

        jobExecution.setFileUpload(existingFileUpload);
        existingFileUpload.getJobExecutions().add(jobExecution);

        // mock per validazione
        when(fileValidator.validate(any(MultipartFile.class)))
                .thenReturn(new ValidationResult());

        // mock per JobExecution
        when(jobExecutionService.createJob(any(FileUpload.class), anyString()))
                .thenReturn(jobExecution);

        // mock per FileStorage
        when(fileStorageService.storeFile(any(MultipartFile.class), anyString()))
                .thenReturn("stored_file_path");
    }

    @Test
    @DisplayName("Verifica che un file con hash già presente nel database venga identificato come duplicato")
    void testIdentifyDuplicateFile() throws IOException {

        // arrange
        when(fileUploadRepository.findByFileHash(fileHash))
                .thenReturn(Optional.of(existingFileUpload));

        // act & assert
        DuplicateFileException exception = assertThrows(DuplicateFileException.class,
                () -> fileUploadService.uploadFile(testFile, username));

        assertTrue(exception.getMessage().contains("File has already been uploaded and processed"));
        assertTrue(exception.getMessage().contains(fileHash));
    }

    @Test
    @DisplayName("Verifica che il caricamento di un file duplicato ma che è fallito, generi un nuovo job")
    void testDuplicateFileGeneratesNewJob() throws IOException {

        // arrange
        FileUpload failedFileUpload = FileUpload.builder()
                .id(1L)
                .filename("test.csv")
                .fileHash(fileHash)
                .uploadDate(LocalDateTime.now().minusDays(1))
                .uploadedBy("previoususer")
                .status(FileUploadStatus.FAILED)
                .build();

        when(fileUploadRepository.findByFileHash(fileHash))
                .thenReturn(Optional.of(failedFileUpload));

        when(fileUploadRepository.save(any(FileUpload.class)))
                .thenReturn(failedFileUpload);

        // act
        fileUploadService.uploadFile(testFile, username);

        // assert
        verify(jobExecutionService).createJob(any(FileUpload.class), eq(username));
        verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("Verifica che l'eccezione DuplicateFileException venga gestita correttamente")
    void testDuplicateFileExceptionHandling() throws IOException {

        // arrange
        when(fileUploadRepository.findByFileHash(fileHash))
                .thenReturn(Optional.of(existingFileUpload));

        // act & assert
        DuplicateFileException exception = assertThrows(DuplicateFileException.class, () -> {
            fileUploadService.uploadFile(testFile, username);
        });

        // verifica che il messaggio di errore contenga informazioni utili
        assertTrue(exception.getMessage().contains(fileHash));
        assertTrue(exception.getMessage().contains(existingFileUpload.getUploadedBy()));
        assertTrue(exception.getMessage().contains("already been uploaded"));
    }

    @Test
    @DisplayName("Verifica che un file in stato PROCESSING venga identificato correttamente")
    void testFileInProcessingState() throws IOException {

        // arrange
        FileUpload processingFileUpload = FileUpload.builder()
                .id(1L)
                .filename("test.csv")
                .fileHash(fileHash)
                .uploadDate(LocalDateTime.now().minusDays(1))
                .uploadedBy("previoususer")
                .status(FileUploadStatus.PROCESSING)
                .jobExecutions(new HashSet<>())
                .build();

        JobExecution processingJob = JobExecution.builder()
                .jobId("job456")
                .status(JobStatus.PROCESSING)
                .startTime(LocalDateTime.now().minusHours(1))
                .fileUpload(processingFileUpload)
                .build();

        processingFileUpload.getJobExecutions().add(processingJob);

        when(fileUploadRepository.findByFileHash(fileHash))
                .thenReturn(Optional.of(processingFileUpload));

        // act & assert
        FileProcessingException exception = assertThrows(FileProcessingException.class,
                () -> fileUploadService.uploadFile(testFile, username));

        assertTrue(exception.getMessage().contains("File is currently being processed"));
        assertTrue(exception.getMessage().contains(fileHash));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public AppProperties appProperties() {
            AppProperties props = new AppProperties();
            AppProperties.FileProcessing fileProcessing = new AppProperties.FileProcessing();
            fileProcessing.setAllowedExtensions(new String[]{"csv"});
            props.setFileProcessing(fileProcessing);
            return props;
        }
    }
}
