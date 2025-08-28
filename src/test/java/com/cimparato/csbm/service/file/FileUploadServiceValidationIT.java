package com.cimparato.csbm.service.file;

import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.domain.event.DomainEventPublisher;
import com.cimparato.csbm.domain.model.FileUpload;
import com.cimparato.csbm.domain.model.JobExecution;
import com.cimparato.csbm.dto.fileupload.FileUploadJobDTO;
import com.cimparato.csbm.mapper.FileUploadMapper;
import com.cimparato.csbm.repository.FileUploadRepository;
import com.cimparato.csbm.service.JobExecutionService;
import com.cimparato.csbm.service.file.storage.FileStorageService;
import com.cimparato.csbm.service.file.validator.FileValidator;
import com.cimparato.csbm.service.file.validator.impl.CloudServiceCsvHeaderRuleFile;
import com.cimparato.csbm.service.file.validator.impl.FileExtensionRuleFile;
import com.cimparato.csbm.service.file.validator.impl.FileNotEmptyRuleFile;
import com.cimparato.csbm.util.FileUtil;
import com.cimparato.csbm.web.rest.errors.FileValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@Import(FileUploadServiceValidationIT.TestConfig.class)
class FileUploadServiceValidationIT {

    @Autowired
    private FileUploadService fileUploadService;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private JobExecutionService jobExecutionService;

    @MockBean
    private FileUploadRepository fileUploadRepository;

    @Test
    @DisplayName("Verifica che un file null generi un'eccezione di validazione")
    void testNullFileThrowsFileValidationException() {

        // act & assert
        FileValidationException exception = assertThrows(
                FileValidationException.class,
                () -> fileUploadService.uploadFile(null, "testUser")
        );

        assertEquals("file cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Verifica che un file vuoto generi un'eccezione di validazione")
    void testEmptyFileThrowsFileValidationException() {

        // arrange
        MultipartFile emptyFile = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                new byte[0]
        );

        // act & assert
        FileValidationException exception = assertThrows(
                FileValidationException.class,
                () -> fileUploadService.uploadFile(emptyFile, "testUser")
        );

        assertTrue(exception.getMessage().contains("File cannot be empty"));
    }

    @Test
    @DisplayName("Verifica che un file con estensione non valida generi un'eccezione di validazione")
    void testInvalidExtensionThrowsFileValidationException() {

        // arrange
        MultipartFile txtFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "content".getBytes()
        );

        // act & assert
        FileValidationException exception = assertThrows(
                FileValidationException.class,
                () -> fileUploadService.uploadFile(txtFile, "testUser")
        );

        assertTrue(exception.getMessage().contains("Allowed file extensions"));
    }

    @Test
    @DisplayName("Verifica che un file con intestazione non valida generi un'eccezione di validazione")
    void testInvalidHeaderThrowsFileValidationException() {

        // arrange
        String csvContent = "wrong,header,columns\ndata1,data2,data3";

        MultipartFile invalidHeaderFile = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent.getBytes()
        );

        // act & assert
        FileValidationException exception = assertThrows(
                FileValidationException.class,
                () -> fileUploadService.uploadFile(invalidHeaderFile, "testUser")
        );

        assertTrue(exception.getMessage().contains("Invalid"));
    }

    @Test
    @DisplayName("Verifica che un file valido passi la validazione")
    void testValidFilePassesValidation() throws Exception {

        // arrange
        String csvContent = "customer_id,service_type,activation_date,expiration_date,amount,status\n" +
                "CUST001,PEC,2023-01-01,2024-01-01,29.99,ACTIVE";

        MultipartFile validFile = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent.getBytes()
        );

        when(fileUploadRepository.findByFileHash(any())).thenReturn(Optional.empty());

        when(fileUploadRepository.save(any(FileUpload.class))).thenAnswer(invocation -> {
            FileUpload savedFile = invocation.getArgument(0);
            // set ID to simulate DB save
            savedFile.setId(1L);
            return savedFile;
        });

        when(jobExecutionService.createJob(any(), anyString())).thenReturn(
                JobExecution.builder()
                        .jobId("test-job-id")
                        .status(JobStatus.PENDING)
                        .build()
        );

        when(fileStorageService.storeFile(any(), anyString())).thenReturn("path/to/file.csv");

        // act
        FileUploadJobDTO result = fileUploadService.uploadFile(validFile, "testUser");

        // assert
        assertNotNull(result, "Result should not be null");
        assertEquals("test.csv", result.getFilename(), "Filename should match");
        assertEquals("test-job-id", result.getJobId(), "Job ID should match");
        assertEquals(JobStatus.PENDING, result.getJobStatus(), "Status should be PENDING");

        // verify interactions
        verify(fileUploadRepository).findByFileHash(any());
        verify(fileUploadRepository).save(any(FileUpload.class));
        verify(jobExecutionService).createJob(any(), eq("testUser"));
        verify(fileStorageService).storeFile(any(), eq("test-job-id"));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public FileValidator fileValidator() {
            FileExtensionRuleFile extensionRule = new FileExtensionRuleFile(appProperties());
            extensionRule.init();

            return new FileValidator(Arrays.asList(
                    new FileNotEmptyRuleFile(),
                    extensionRule,
                    new CloudServiceCsvHeaderRuleFile()
            ));
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

        @Bean
        public AppProperties appProperties() {
            AppProperties props = new AppProperties();
            AppProperties.FileProcessing fileProcessing = new AppProperties.FileProcessing();
            fileProcessing.setAllowedExtensions(new String[]{"csv"});
            props.setFileProcessing(fileProcessing);
            return props;
        }

        @Bean
        public FileUploadMapper fileUploadMapper() {
            return Mockito.mock(FileUploadMapper.class);
        }

        @Bean
        public DomainEventPublisher eventPublisher() {
            return Mockito.mock(DomainEventPublisher.class);
        }

        @Bean
        public FileUtil fileUtil() {
            return new FileUtil();
        }
    }
}
