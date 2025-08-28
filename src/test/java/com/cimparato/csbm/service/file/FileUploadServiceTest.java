package com.cimparato.csbm.service.file;

import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.domain.event.DomainEventPublisher;
import com.cimparato.csbm.domain.event.JobCreatedEvent;
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
import com.cimparato.csbm.web.rest.errors.FileProcessingException;
import com.cimparato.csbm.web.rest.errors.FileValidationException;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@Import({FileUploadService.class})
class FileUploadServiceTest {

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
    private ArgumentCaptor<JobCreatedEvent> eventCaptor;

    private MultipartFile validFile;
    private String username = "testuser";
    private ValidationResult validationResult;
    private FileUpload newFileUpload;
    private JobExecution jobExecution;

    @BeforeEach
    void setUp() {
        validFile = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                "test content".getBytes()
        );

        validationResult = new ValidationResult();

        newFileUpload = FileUpload.builder()
                .id(1L)
                .filename("test.csv")
                .fileHash("abc123")
                .uploadDate(LocalDateTime.now())
                .uploadedBy(username)
                .status(FileUploadStatus.PENDING)
                .build();

        jobExecution = JobExecution.builder()
                .jobId("job123")
                .status(JobStatus.PENDING)
                .startTime(LocalDateTime.now())
                .fileUpload(newFileUpload)
                .build();

        // mock per validazione
        when(fileValidator.validate(any(MultipartFile.class)))
                .thenReturn(validationResult);

        // mock per repository
        when(fileUploadRepository.findByFileHash(anyString()))
                .thenReturn(Optional.empty());
        when(fileUploadRepository.save(any(FileUpload.class)))
                .thenReturn(newFileUpload);

        // mock per JobExecution
        when(jobExecutionService.createJob(any(FileUpload.class), anyString()))
                .thenReturn(jobExecution);

        // mock per FileStorage
        when(fileStorageService.storeFile(any(MultipartFile.class), anyString()))
                .thenReturn("stored_file_path");
    }

    @Test
    @DisplayName("Verifica che un file valido venga caricato correttamente")
    void testValidFileUpload() {

        // act
        FileUploadJobDTO result = fileUploadService.uploadFile(validFile, username);

        // assert
        assertNotNull(result);
        assertEquals("test.csv", result.getFilename());
        assertEquals("abc123", result.getFileHash());
        assertEquals("job123", result.getJobId());
        assertEquals(JobStatus.PENDING, result.getJobStatus());
    }

    @Test
    @DisplayName("Verifica che il file venga salvato correttamente nel file system")
    void testFileSavedToFileSystem() {

        // act
        fileUploadService.uploadFile(validFile, username);

        // assert
        verify(fileStorageService).storeFile(eq(validFile), eq("job123"));
    }

    @Test
    @DisplayName("Verifica che i dati del file vengano salvati correttamente nel database")
    void testFileMetadataSavedToDatabase() {

        // act
        fileUploadService.uploadFile(validFile, username);

        // assert
        verify(fileUploadRepository).save(any(FileUpload.class));
    }

    @Test
    @DisplayName("Verifica che venga creato un job per il file caricato")
    void testJobCreatedForUploadedFile() {

        // act
        fileUploadService.uploadFile(validFile, username);

        // assert
        verify(jobExecutionService).createJob(any(FileUpload.class), eq(username));
    }

    @Test
    @DisplayName("Verifica che venga pubblicato un evento JobCreatedEvent")
    void testJobCreatedEventPublished() {

        // act
        fileUploadService.uploadFile(validFile, username);

        // assert
        verify(eventPublisher).publish(eventCaptor.capture());

        JobCreatedEvent capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent);
        assertEquals("job123", capturedEvent.getJobId());
    }

    @Test
    @DisplayName("Verifica che un file null generi un'eccezione FileValidationException")
    void testNullFileThrowsException() {

        // act & assert
        FileValidationException exception = assertThrows(FileValidationException.class, () -> {
            fileUploadService.uploadFile(null, username);
        });

        assertEquals("file cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Verifica che un file non valido generi un'eccezione FileValidationException")
    void testInvalidFileThrowsException() {

        // arrange
        ValidationResult invalidResult = new ValidationResult();
        invalidResult.addError("Invalid file format");
        invalidResult.addError("File too large");

        when(fileValidator.validate(any(MultipartFile.class)))
                .thenReturn(invalidResult);

        // act & assert
        FileValidationException exception = assertThrows(FileValidationException.class, () -> {
            fileUploadService.uploadFile(validFile, username);
        });

        assertEquals("Invalid file format, File too large", exception.getMessage());
    }

    @Test
    @DisplayName("Verifica che un errore durante il salvataggio del file generi un'eccezione FileProcessingException")
    void testFileStorageErrorThrowsException() {

        // arrange
        when(fileStorageService.storeFile(any(MultipartFile.class), anyString()))
                .thenThrow(new RuntimeException("Storage error"));

        // act & assert
        FileProcessingException exception = assertThrows(FileProcessingException.class, () -> {
            fileUploadService.uploadFile(validFile, username);
        });

        assertTrue(exception.getMessage().contains("Could not process file"));
    }

    @Test
    @DisplayName("Verifica che un errore imprevisto venga gestito correttamente")
    void testUnexpectedErrorHandling() {

        // arrange
        when(fileUploadRepository.save(any(FileUpload.class)))
                .thenThrow(new RuntimeException("Database error"));

        // act & assert
        FileProcessingException exception = assertThrows(FileProcessingException.class,
                () -> fileUploadService.uploadFile(validFile, username));

        assertTrue(exception.getMessage().contains("Could not process file"));
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
