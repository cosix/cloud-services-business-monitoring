package com.cimparato.csbm.service.file;

import com.cimparato.csbm.config.async.FileProcessingTaskExecutor;
import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.domain.event.DomainEventPublisher;
import com.cimparato.csbm.domain.event.FileProcessingCompletedEvent;
import com.cimparato.csbm.domain.event.JobCreatedEvent;
import com.cimparato.csbm.domain.file.FileUploadStatus;
import com.cimparato.csbm.domain.model.CloudService;
import com.cimparato.csbm.domain.model.FileUpload;
import com.cimparato.csbm.domain.model.JobExecution;
import com.cimparato.csbm.domain.model.ProcessingError;
import com.cimparato.csbm.dto.cloudservice.CloudServiceDTO;
import com.cimparato.csbm.dto.jobexecution.JobStatusDTO;
import com.cimparato.csbm.mapper.CloudServiceMapper;
import com.cimparato.csbm.mapper.ProcessingErrorMapper;
import com.cimparato.csbm.repository.*;
import com.cimparato.csbm.service.CloudServiceService;
import com.cimparato.csbm.service.JobExecutionService;
import com.cimparato.csbm.service.file.parser.FileParser;
import com.cimparato.csbm.service.file.parser.FileParserStrategy;
import com.cimparato.csbm.service.file.storage.FileStorageService;
import com.cimparato.csbm.web.rest.errors.FileStorageException;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@Import({FileProcessorService.class})
class FileProcessorServiceIT {

    @MockBean
    private JobExecutionService jobExecutionService;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private CloudServiceService cloudServiceService;

    @MockBean
    private FileUploadRepository fileUploadRepository;

    @MockBean
    private JobExecutionRepository jobExecutionRepository;

    @MockBean
    private CloudServiceRepository cloudServiceRepository;

    @MockBean
    private ProcessingErrorRepository processingErrorRepository;

    @MockBean
    private ServiceFileRelationRepository serviceFileRelationRepository;

    @MockBean
    private CloudServiceMapper cloudServiceMapper;

    @MockBean
    private ProcessingErrorMapper processingErrorMapper;

    @MockBean
    private FileParserStrategy fileParserStrategy;

    @MockBean
    private DomainEventPublisher eventPublisher;

    @MockBean
    private FileProcessingTaskExecutor fileProcessingTaskExecutor;

    @Autowired
    private FileProcessorService fileProcessorService;

    @Autowired
    private AppProperties appProperties;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;

    private JobCreatedEvent jobCreatedEvent;
    private JobExecution jobExecution;
    private JobExecution jobExecutionFailed;
    private JobExecution jobExecutionCompleted;
    private FileUpload fileUpload;
    private String jobId = "job123";
    private String fileHash = "abc123";
    private String csvContent;

    @BeforeEach
    void setUp() {
        
        csvContent = "customer_id,service_type,activation_date,expiration_date,amount,status\n" +
                "CUST001,PEC,2023-01-01,2024-01-01,29.99,ACTIVE\n" +
                "CUST002,HOSTING,2023-02-15,2024-02-15,120.50,ACTIVE\n";
        
        JobStatusDTO jobStatusDTO = JobStatusDTO.builder()
                .jobId(jobId)
                .status(JobStatus.PENDING)
                .build();
        jobCreatedEvent = new JobCreatedEvent(jobStatusDTO);
        
        fileUpload = FileUpload.builder()
                .id(1L)
                .filename("test.csv")
                .fileHash(fileHash)
                .uploadDate(LocalDateTime.now())
                .uploadedBy("testuser")
                .status(FileUploadStatus.PENDING)
                .build();
        
        jobExecution = JobExecution.builder()
                .jobId(jobId)
                .status(JobStatus.PENDING)
                .startTime(LocalDateTime.now())
                .fileUpload(fileUpload)
                .filePath("path/to/file.csv")
                .build();

        jobExecutionFailed = JobExecution.builder()
                .jobId(jobExecution.getJobId())
                .status(JobStatus.FAILED)
                .startTime(jobExecution.getStartTime())
                .fileUpload(jobExecution.getFileUpload())
                .filePath("path/to/file.csv")
                .build();

        jobExecutionCompleted = JobExecution.builder()
                .jobId(jobExecution.getJobId())
                .status(JobStatus.COMPLETED)
                .startTime(jobExecution.getStartTime())
                .fileUpload(jobExecution.getFileUpload())
                .filePath("path/to/file.csv")
                .build();

        // Configurazione dei mock
        when(jobExecutionService.getJobExecutionById(jobId))
                .thenReturn(jobExecution);

        when(fileStorageService.loadFileAsResource(anyString()))
                .thenReturn(new ByteArrayResource(csvContent.getBytes()));

        // Configura il comportamento del FileProcessingTaskExecutor
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run(); // Esegui il task immediatamente invece di schedularlo
            return null;
        }).when(fileProcessingTaskExecutor).executeWithJobId(any(Runnable.class), anyString());
    }

    @Test
    @DisplayName("Verifica che il file venga elaborato correttamente quando viene ricevuto un JobCreatedEvent")
    void testFileProcessingOnJobCreatedEvent() {
        
        // arrange
        FileParser<CloudServiceDTO> mockParser = mock(FileParser.class);
        when(fileParserStrategy.getParser(anyString(), eq(CloudServiceDTO.class)))
                .thenReturn(mockParser);

        // act
        fileProcessorService.scheduleFileProcessing(jobCreatedEvent);

        // assert
        verify(jobExecutionService).updateJobStatus(jobId, JobStatus.PROCESSING, null);
        verify(fileStorageService).loadFileAsResource(jobExecution.getFilePath());
        verify(fileParserStrategy).getParser("csv", CloudServiceDTO.class);
        verify(mockParser).parse(any(), any());
    }

    @Test
    @DisplayName("Verifica che lo stato del job venga aggiornato a COMPLETED dopo l'elaborazione")
    void testJobStatusUpdatedToCompletedAfterProcessing() {

        // arrange
        FileParser<CloudServiceDTO> mockParser = mock(FileParser.class);
        when(fileParserStrategy.getParser(anyString(), eq(CloudServiceDTO.class)))
                .thenReturn(mockParser);

        when(mockParser.getParsingErrors()).thenReturn(Collections.emptyList());

        when(fileUploadRepository.save(any(FileUpload.class)))
                .thenReturn(fileUpload);

        when(jobExecutionService.updateJobStatus(eq(jobId), eq(JobStatus.COMPLETED), isNull()))
                .thenReturn(jobExecutionCompleted);

        ProcessingError mockProcessingError = mock(ProcessingError.class);
        when(processingErrorMapper.toEntity(any())).thenReturn(mockProcessingError);
        when(processingErrorMapper.convertParsingErrorsTo(anyList(), any(FileUpload.class)))
                .thenReturn(Collections.emptyList());

        when(processingErrorRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        when(cloudServiceRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(serviceFileRelationRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        // configura il parser per simulare l'elaborazione di record validi
        doAnswer(invocation -> {
            Consumer<CloudServiceDTO> processor = invocation.getArgument(1);
            CloudServiceDTO dto = new CloudServiceDTO();
            dto.setCustomerId("CUST001");
            processor.accept(dto);
            return null;
        }).when(mockParser).parse(any(), any());

        CloudService mockCloudService = mock(CloudService.class);
        when(cloudServiceMapper.toEntity(any(CloudServiceDTO.class))).thenReturn(mockCloudService);

        // act
        fileProcessorService.scheduleFileProcessing(jobCreatedEvent);

        // assert
        verify(jobExecutionService).updateJobStatus(eq(jobId), eq(JobStatus.COMPLETED), any());
        verify(fileUploadRepository, times(1)).save(any(FileUpload.class));
        verify(eventPublisher).publish(any(FileProcessingCompletedEvent.class));
    }

    @Test
    @DisplayName("Verifica che lo stato del job venga aggiornato a FAILED in caso di errore")
    void testJobStatusUpdatedToFailedOnError() throws Exception {

        // arrange
        when(fileStorageService.loadFileAsResource(anyString()))
                .thenThrow(new FileStorageException("File not found"));

        when(fileUploadRepository.save(any(FileUpload.class)))
                .thenReturn(fileUpload);

        when(jobExecutionRepository.findByJobId(jobId))
                .thenReturn(Optional.of(jobExecution));

        when(jobExecutionService.updateJobStatus(jobId, JobStatus.FAILED, "File not found"))
                .thenReturn(jobExecutionFailed);

        // act
        fileProcessorService.scheduleFileProcessing(jobCreatedEvent);

        // assert
        verify(jobExecutionService).updateJobStatus(eq(jobId), eq(JobStatus.FAILED), any());
        verify(fileUploadRepository).save(any(FileUpload.class));
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
    }
}
