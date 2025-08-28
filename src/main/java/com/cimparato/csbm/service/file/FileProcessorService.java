package com.cimparato.csbm.service.file;

import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.config.async.FileProcessingTaskExecutor;
import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.domain.event.FileProcessingCompletedEvent;
import com.cimparato.csbm.domain.event.JobCreatedEvent;
import com.cimparato.csbm.domain.file.FileErrorType;
import com.cimparato.csbm.domain.file.FileOperationType;
import com.cimparato.csbm.domain.file.FileUploadStatus;
import com.cimparato.csbm.domain.model.*;
import com.cimparato.csbm.dto.cloudservice.CloudServiceDTO;
import com.cimparato.csbm.dto.fileupload.FileUploadJobDTO;
import com.cimparato.csbm.dto.processingerror.ProcessingErrorCreateDTO;
import com.cimparato.csbm.mapper.CloudServiceMapper;
import com.cimparato.csbm.mapper.ProcessingErrorMapper;
import com.cimparato.csbm.repository.CloudServiceRepository;
import com.cimparato.csbm.repository.FileUploadRepository;
import com.cimparato.csbm.repository.ProcessingErrorRepository;
import com.cimparato.csbm.repository.ServiceFileRelationRepository;
import com.cimparato.csbm.service.CloudServiceService;
import com.cimparato.csbm.service.file.parser.FileParser;
import com.cimparato.csbm.service.file.parser.FileParserStrategy;
import com.cimparato.csbm.service.file.parser.ParsingError;
import com.cimparato.csbm.service.file.storage.FileStorageService;
import com.cimparato.csbm.service.JobExecutionService;
import com.cimparato.csbm.domain.event.DomainEventPublisher;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileProcessorService {

    private int batchSize;

    private final AppProperties appProperties;

    private final JobExecutionService jobExecutionService;
    private final FileStorageService fileStorageService;
    private final CloudServiceService cloudServiceService;

    private final FileUploadRepository fileUploadRepository;
    private final CloudServiceRepository cloudServiceRepository;
    private final ProcessingErrorRepository processingErrorRepository;
    private final ServiceFileRelationRepository serviceFileRelationRepository;

    private final CloudServiceMapper cloudServiceMapper;
    private final ProcessingErrorMapper processingErrorMapper;

    private final FileParserStrategy fileParserStrategy;
    private final DomainEventPublisher eventPublisher;

    private final FileProcessingTaskExecutor fileProcessingTaskExecutor;

    public FileProcessorService(
            AppProperties appProperties,
            JobExecutionService jobExecutionService,
            FileStorageService fileStorageService,
            CloudServiceService cloudServiceService,
            FileUploadRepository fileUploadRepository,
            CloudServiceRepository cloudServiceRepository,
            ProcessingErrorRepository processingErrorRepository,
            ServiceFileRelationRepository serviceFileRelationRepository,
            CloudServiceMapper cloudServiceMapper,
            ProcessingErrorMapper processingErrorMapper,
            FileParserStrategy fileParserStrategy,
            DomainEventPublisher eventPublisher,
            FileProcessingTaskExecutor fileProcessingTaskExecutor
    ) {
        this.appProperties = appProperties;
        this.jobExecutionService = jobExecutionService;
        this.fileStorageService = fileStorageService;
        this.cloudServiceService = cloudServiceService;
        this.fileUploadRepository = fileUploadRepository;
        this.cloudServiceRepository = cloudServiceRepository;
        this.processingErrorRepository = processingErrorRepository;
        this.serviceFileRelationRepository = serviceFileRelationRepository;
        this.cloudServiceMapper = cloudServiceMapper;
        this.processingErrorMapper = processingErrorMapper;
        this.fileParserStrategy = fileParserStrategy;
        this.eventPublisher = eventPublisher;
        this.fileProcessingTaskExecutor = fileProcessingTaskExecutor;
    }

    @PostConstruct
    public void init() {
        this.batchSize = appProperties.getFileProcessing().getBatchSize();
    }

    /**
     * Pianifica l'elaborazione asincrona di un file precedentemente caricato.
     *
     * Questo metodo riceve un JobCreatedEvent che viene pubblicato dopo il commit della transazione che ha creato il job.
     * Il metodo viene eseguito dopo il commit della transazione che ha creato il job,
     * grazie all'annotazione @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT).
     * Ciò garantisce che il job sia stato correttamente persistito nel database prima di
     * iniziare l'elaborazione, evitando problemi che potrebbero verificarsi se il metodo asincrono venisse chiamato
     * direttamente prima del commit della transazione principale.
     *
     * Il metodo sottomette un task al FileProcessingTaskExecutor per l'elaborazione asincrona
     * del file. Se il task viene rifiutato (ad esempio perché il thread pool è saturo),
     * o se si verifica qualsiasi altra eccezione durante la pianificazione, lo stato del job
     * viene aggiornato a FAILED con un messaggio appropriato.
     *
     * @param event L'evento ricevuto contenente l'id univoco del job di elaborazione
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void scheduleFileProcessing(JobCreatedEvent event) {
        var jobId = event.getJobId();

        if (jobId == null || jobId.isEmpty()) {
            log.error("Cannot schedule processing for null or empty jobId");
            return;
        }

        log.info("Scheduling asynchronous processing for job: {}", jobId);

        try {

            fileProcessingTaskExecutor.executeWithJobId(() -> {
                log.info("Starting file processing for jobId:", jobId);
                processFile(jobId);
                log.info("File processing completed for jobId:", jobId);
            }, jobId);

        } catch (Exception e) {
            handleSchedulingError(e, jobId);
        }
    }

    /**
     * Elabora il file associato al job specificato in un thread separato.
     *
     * Questo metodo:
     * 1. Carica il file dal disco
     * 2. Lo analizza con il parser appropriato
     * 3. Salva i dati estratti nel database
     * 4. Aggiorna lo stato del job e del file al termine dell'elaborazione
     * 5. pubblica un evento di completamento per avviare i processi successivi (gestione notifiche).
     *
     * L'elaborazione avviene in modo efficiente tramite streaming, ogni record viene processato appena letto dal
     * parser, senza caricare l'intero file in memoria. Questo approccio permette di gestire file di grandi dimensioni
     * senza problemi di memoria.
     *
     * Per ottimizzare le operazioni di database, i record vengono salvati in batch di dimensione predefinita,
     * ogni batch viene salvato in una propria transazione tramite il metodo saveCurrentBatch.
     * Questo approccio offre diversi vantaggi:
     * - Evita timeout con file molto grandi
     * - Riduce il consumo di memoria e risorse del database
     * - Garantisce che i batch già elaborati vengano salvati anche se si verificano errori durante l'elaborazione di
     * batch successivi.
     *
     * Gli errori di parsing e di elaborazione vengono salvati ma non interrompono il processo, permettendo di
     * identificare tutti i problemi presenti nel file. Al termine dell'elaborazione, lo stato del job e del file
     * viene aggiornato in una transazione separata tramite il metodo completeProcessing.
     *
     * In caso di errori parziali, l'utente può correggere il file e ricaricarlo.
     *
     * @param jobId L'identificativo univoco del job di elaborazione
     */
    private void processFile(String jobId) {
        log.info("Starting asynchronous processing for job: {}", jobId);

        JobExecution job = jobExecutionService.getJobExecutionById(jobId);
        FileUpload fileUpload = job.getFileUpload();

        try {
            jobExecutionService.updateJobStatus(jobId, JobStatus.PROCESSING, null);

            String fileExtension = getFileExtension(fileUpload.getFilename());

            FileParser<CloudServiceDTO> parser = fileParserStrategy.getParser(fileExtension, CloudServiceDTO.class);

            int batchSize = appProperties.getFileProcessing().getBatchSize();

            ProcessingContext context = ProcessingContext.createWithBatchSize(batchSize);

            Resource fileResource = fileStorageService.loadFileAsResource(job.getFilePath());

            try (InputStream inputStream = fileResource.getInputStream()) {

                parser.parse(inputStream, dto -> processRecord(dto, fileUpload, context));

                if (!context.batchServices.isEmpty()) {
                    log.debug("Saving final batch of {} records", context.batchServices.size());
                    saveCurrentBatch(context.batchServices, context.batchRelations);
                }
            }

            List<ProcessingError> allErrors = processErrors(parser.getParsingErrors(), context.processingErrors, fileUpload);

            completeProcessing(job, fileUpload, context, allErrors.size());

            clearProcessingContext(context);

        } catch (Exception ex) {
            handleProcessingFailure(job, fileUpload, ex);
        }

    }

    private void clearProcessingContext(ProcessingContext processingContext) {
        processingContext.batchServices.clear();
        processingContext.batchRelations.clear();
        processingContext.processingErrors.clear();
        processingContext.validRecords = 0;
    }

    private String getFileExtension(String filename) {
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /**
     * Elabora un singolo record del file e lo aggiunge al batch corrente per il salvataggio.
     *
     * Questo metodo viene utilizzato come callback dal parser CSV e viene chiamato per ogni riga valida
     * letta dal file. Si occupa di:
     * - Incrementare i contatori dei record totali
     * - Preparare le entità CloudService e ServiceFileRelation a partire dal DTO
     * - Aggiungere le entità al batch corrente per il salvataggio
     * - Salvare il batch quando raggiunge la dimensione massima configurata
     * - Gestire eventuali errori durante l'elaborazione del record
     *
     * In caso di errore durante l'elaborazione di un record, viene registrato come errore di
     * processing ma non interrompe l'elaborazione degli altri record consentendo di
     * identificare tutti i problemi presenti nel file senza bloccare l'intero processo.
     *
     * Il metodo utilizza un oggetto ProcessingContext per mantenere lo stato dell'elaborazione,
     * inclusi i contatori e le liste temporanee per il batch processing.
     *
     * @param dto Il DTO contenente i dati del record da elaborare
     * @param fileUpload L'entità che rappresenta il file in elaborazione
     * @param context Il contesto che mantiene lo stato dell'elaborazione, inclusi contatori e batch corrente
     */
    private void processRecord(CloudServiceDTO dto, FileUpload fileUpload, ProcessingContext context) {

        try {
            Pair<CloudService, ServiceFileRelation> result = prepareServiceRecord(dto, fileUpload);

            // Aggiungi al batch corrente
            context.batchServices.add(result.getFirst());
            context.batchRelations.add(result.getSecond());
            context.validRecords++;

            // Se il batch ha raggiunto la dimensione massima, salvalo e inizia un nuovo batch
            if (context.batchServices.size() >= batchSize) {
                log.debug("Batch size reached ({}). Saving batch and clearing lists", batchSize);
                saveCurrentBatch(context.batchServices, context.batchRelations);
                context.batchServices.clear();
                context.batchRelations.clear();
            }
        } catch (Exception e) {
            var lineNumber = dto.getLineNumber();
            log.warn("Error processing record at line {}: {}", lineNumber, e.getMessage());

            context.processingErrors.add(ProcessingErrorCreateDTO.builder()
                    .lineNumber(lineNumber)
                    .rawData(dto.toString())
                    .errorMessage(e.getMessage())
                    .errorType(FileErrorType.PROCESSING_ERROR)
                    .build());
        }
    }

    /**
     * Completa l'elaborazione del file aggiornando lo stato del file caricato e dell'esecuzione del job.
     *
     * Il metodo viene eseguito all'interno del contesto transazionale dal metodo processFile,
     * garantendo che tutti gli aggiornamenti al database vengano fatti in modo atomico.
     *
     * @param job L'entità JobExecution associata all'elaborazione del file
     * @param fileUpload L'entità che rappresenta il file elaborato
     * @param context Il contesto di elaborazione contenente le statistiche sui record processati
     * @param invalidRecordsCount Il numero di record non validi riscontrati durante l'elaborazione
     * @return le entità FileUpload e JobExecution aggiornate
     */
    @Transactional
    private Pair<FileUpload, JobExecution> completeProcessing(JobExecution job, FileUpload fileUpload,
                                    ProcessingContext context, int invalidRecordsCount) {
        int validRecordsCount = context.validRecords;
        int totalRecordsCount = validRecordsCount + invalidRecordsCount;

        fileUpload.setStatus(FileUploadStatus.COMPLETED);
        fileUpload.setInvalidRecords(invalidRecordsCount);
        fileUpload.setValidRecords(validRecordsCount);
        fileUpload.setTotalRecords(totalRecordsCount);
        var fileUploadSaved = fileUploadRepository.save(fileUpload);

        var jobExecutionSaved = jobExecutionService.updateJobStatus(job.getJobId(), JobStatus.COMPLETED, null);

        log.info("Completed processing of job: {}. Total Records: {}, Valid: {}, Invalid: {}",
                job.getJobId(), totalRecordsCount, validRecordsCount, invalidRecordsCount);

        var fileUploadJobDTO = FileUploadJobDTO.builder()
                .fileHash(fileUploadSaved.getFileHash())
                .filename(fileUploadSaved.getFilename())
                .jobId(jobExecutionSaved.getJobId())
                .jobStatus(jobExecutionSaved.getStatus())
                .build();

        log.info("Publishing event `FileProcessingCompletedEvent` for file with hash: {}", fileUploadSaved.getFileHash());

        eventPublisher.publish(new FileProcessingCompletedEvent(fileUploadJobDTO));

        return Pair.of(fileUploadSaved, jobExecutionSaved);
    }

    /**
     * Gestisce le eccezioni che possono verificarsi durante l'elaborazione del file.
     *
     * Questo metodo aggiorna lo stato del job e del file upload a FAILED e registra
     * l'errore nel log. L'operazione viene eseguita in un contesto transazionale
     * per garantire che tutti gli aggiornamenti di stato vengano persistiti atomicamente.
     */
    @Transactional
    private void handleProcessingFailure(JobExecution job, FileUpload fileUpload, Exception ex) {
        log.error("Failed to process file for job: {}. Error: {}", job.getJobId(), ex.getMessage(), ex);

        jobExecutionService.updateJobStatus(job.getJobId(), JobStatus.FAILED, ex.getMessage());

        fileUpload.setStatus(FileUploadStatus.FAILED);
        fileUploadRepository.save(fileUpload);
    }

    @Transactional
    private void saveCurrentBatch(List<CloudService> services, List<ServiceFileRelation> relations) {
        cloudServiceRepository.saveAll(services);
        serviceFileRelationRepository.saveAll(relations);
    }

    /**
     * Elabora e salva nel database gli errori riscontrati durante il parsing e l'elaborazione del file.
     *
     * @param parsingErrors Lista degli errori riscontrati durante il parsing del file
     * @param processingErrorDTOs Lista degli errori riscontrati durante il processing del file
     * @param fileUpload L'entità che rappresenta il file a cui associare gli errori
     * @return Lista delle entità ProcessingError salvate nel database
     */
    @Transactional
    private List<ProcessingError> processErrors(List<ParsingError> parsingErrors,
                                                List<ProcessingErrorCreateDTO> processingErrorDTOs,
                                                FileUpload fileUpload) {

        if (CollectionUtils.isEmpty(parsingErrors) && CollectionUtils.isEmpty(processingErrorDTOs)) {
            return Collections.emptyList();
        }

        List<ProcessingError> allErrors = new ArrayList<>();

        // Converte ProcessingErrorDTOs in entità ProcessingError
        if (!CollectionUtils.isEmpty(processingErrorDTOs)) {
            allErrors = processingErrorDTOs.stream()
                    .map(dto -> {
                        ProcessingError entity = processingErrorMapper.toEntity(dto);
                        entity.setFileUpload(fileUpload);
                        return entity;
                    })
                    .collect(Collectors.toList());
        }

        // Converte ParsingError in entità ProcessingError
        if (!CollectionUtils.isEmpty(parsingErrors)) {
            allErrors.addAll(processingErrorMapper.convertParsingErrorsTo(parsingErrors, fileUpload));
        }

        List<ProcessingError> savedErrors = processingErrorRepository.saveAll(allErrors);

        return savedErrors;
    }

    private Pair<CloudService, ServiceFileRelation> prepareServiceRecord(CloudServiceDTO dto, FileUpload fileUpload) {
        Optional<CloudServiceDTO> existingService = cloudServiceService
                .findByCustomerIdAndServiceType(dto.getCustomerId(), dto.getServiceType());

        CloudService cloudService;
        boolean isUpdate = false;

        if (existingService.isPresent()) {
            cloudService = cloudServiceMapper.toEntity(existingService.get());
            updateExistingService(cloudService, dto);
            log.debug("Updated existing service: customerId={}, serviceType={}", dto.getCustomerId(), dto.getServiceType());
            isUpdate = true;
        } else {
            cloudService = cloudServiceMapper.toEntity(dto);
            log.debug("Created new service: customerId={}, serviceType={}", dto.getCustomerId(), dto.getServiceType());
        }

        // Crea la relazione fra CloudService e FileUpload
        var serviceFileRelation = ServiceFileRelation.builder()
                .service(cloudService)
                .fileUpload(fileUpload)
                .operationType(isUpdate ? FileOperationType.UPDATE : FileOperationType.CREATE)
                .lineNumber(dto.getLineNumber())
                .build();

        return Pair.of(cloudService, serviceFileRelation);
    }

    private void updateExistingService(CloudService service, CloudServiceDTO dto) {
        service.setActivationDate(dto.getActivationDate());
        service.setExpirationDate(dto.getExpirationDate());
        service.setAmount(dto.getAmount());
        service.setStatus(dto.getStatus());
        service.setLastUpdated(LocalDateTime.now());
    }

    private void handleSchedulingError(Exception e, String jobId) {
        String errorMessage;

        if (e instanceof RejectedExecutionException) {
            // cattura l'eccezione di scheduling lanciata dalla policy di rifiuto del taskExecutor
            log.error("Failed to schedule processing for job {} due to system overload: {}", jobId, e.getMessage());
            errorMessage = "System overloaded. Please try again later.";
        } else {
            log.error("Unexpected error while scheduling processing for job {}: {}", jobId, e.getMessage(), e);
            errorMessage = "Failed to schedule processing: " + e.getMessage();
        }

        try {
            jobExecutionService.updateJobStatus(jobId, JobStatus.FAILED, errorMessage);
        } catch (Exception innerException) {
            log.error("Failed to update job status for job {}: {}", jobId, innerException.getMessage(), innerException);
        }
    }

}
