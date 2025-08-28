package com.cimparato.csbm.service.file;

import com.cimparato.csbm.aop.logging.LogMethod;
import com.cimparato.csbm.domain.event.JobCreatedEvent;
import com.cimparato.csbm.domain.file.ValidationResult;
import com.cimparato.csbm.domain.model.FileUpload;
import com.cimparato.csbm.domain.model.JobExecution;
import com.cimparato.csbm.dto.fileupload.FileUploadJobDTO;
import com.cimparato.csbm.dto.fileupload.FileUploadSummaryDTO;
import com.cimparato.csbm.dto.jobexecution.JobStatusDTO;
import com.cimparato.csbm.mapper.FileUploadMapper;
import com.cimparato.csbm.repository.FileUploadRepository;
import com.cimparato.csbm.dto.fileupload.FileUploadDTO;
import com.cimparato.csbm.domain.file.FileUploadStatus;
import com.cimparato.csbm.service.file.storage.FileStorageService;
import com.cimparato.csbm.service.file.validator.FileValidator;
import com.cimparato.csbm.service.JobExecutionService;
import com.cimparato.csbm.domain.event.DomainEventPublisher;
import com.cimparato.csbm.util.FileUtil;
import com.cimparato.csbm.web.rest.errors.DuplicateFileException;
import com.cimparato.csbm.web.rest.errors.FileProcessingException;
import com.cimparato.csbm.web.rest.errors.FileValidationException;
import com.cimparato.csbm.web.rest.errors.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.boot.logging.LogLevel;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Comparator;

@Slf4j
@Service
public class FileUploadService {

    private final FileStorageService fileStorageService;
    private final JobExecutionService jobExecutionService;
    private final FileValidator fileValidator;
    private final FileUploadRepository fileUploadRepository;
    private final FileUploadMapper fileUploadMapper;
    private final DomainEventPublisher eventPublisher;

    public FileUploadService(FileStorageService fileStorageService, JobExecutionService jobExecutionService, FileValidator fileValidator, FileUploadRepository fileUploadRepository, FileUploadMapper fileUploadMapper, DomainEventPublisher eventPublisher) {
        this.fileStorageService = fileStorageService;
        this.jobExecutionService = jobExecutionService;
        this.fileValidator = fileValidator;
        this.fileUploadRepository = fileUploadRepository;
        this.fileUploadMapper = fileUploadMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Gestisce il caricamento di un file CSV contenente dati sui servizi cloud, eseguendo la validazione,
     * l'elaborazione e l'archiviazione del file.
     *
     * Il metodo verifica innanzitutto che il file non sia nullo e ne valida il formato e il contenuto
     * tramite un validatore. Successivamente, calcola l'hash del file per verificare se è già stato
     * caricato in precedenza. Se il file è nuovo, viene creato un nuovo record, se invece
     * esiste già, viene gestito in base al suo stato attuale.
     *
     * Dopo aver gestito il record, il metodo crea un job, salva il file nel filesystem e avvia l'elaborazione asincrona.
     * Il metodo non attende il completamento dell'elaborazione, che avviene in background, e non si occupa direttamente
     * del parsing o della validazione del contenuto del file CSV, né della persistenza dei dati estratti,
     * delegando queste responsabilità al servizio di elaborazione.
     *
     * In caso di errori durante il processo, possono essere lanciate diverse eccezioni: FileValidationException
     * se il file non supera la validazione, DuplicateFileException se il file è già stato caricato e processato,
     * o FileProcessingException per altri errori durante il processo.
     *
     * NOTA SULLA PUBBLICAZIONE DELL'EVENTO:
     * Al termine del processo di upload, viene pubblicato l'evento `JobCreatedEvent` contenente l'ID del job creato.
     * Questo evento viene intercettato da un listener che avvia l'elaborazione asincrona del file solo dopo che la
     * transazione corrente è stata committata. Questo è possibile grazie all'utilizzo
     * di @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT).
     * Ciò garantisce che il job e il file siano correttamente persistiti nel database prima dell'inizio dell'elaborazione.
     *
     * @param file Il file da caricare e processare, non può essere null
     * @param username Il nome utente dell'utente che sta caricando il file
     * @return Un oggetto contenente le informazioni sul caricamento e sul job di elaborazione
     * @throws FileValidationException se il file è nullo o non è valido
     * @throws DuplicateFileException se il file è già stato caricato e processato
     * @throws FileProcessingException se il file è in fase di elaborazione o si verifica un errore
     */
    @LogMethod(logParams = true, logResult = true, measureTime = true, level = LogLevel.INFO)
    @Transactional
    public FileUploadJobDTO uploadFile(MultipartFile file, String username) {
        if (file == null) {
            throw new FileValidationException("file cannot be null");
        }

        String filename = file.getOriginalFilename();
        long fileSize = file.getSize();

        log.info("Starting file upload process for file '{}' ({} bytes) by user '{}'", filename, fileSize, username);

        ValidationResult validationResult = fileValidator.validate(file);
        if (!validationResult.isValid()) {
            throw new FileValidationException(String.join(", ", validationResult.getErrors()));
        }
        log.debug("File '{}' passed validation checks", filename);

        try {
            var fileExtension = filename.substring(filename.lastIndexOf(".") + 1);
            log.debug("File extension: {}", fileExtension);

            var fileHash = FileUtil.calculateFileHash(file);
            log.debug("Calculated file hash: {}", fileHash);

            FileUpload fileUpload;
            var existingFileOpt = fileUploadRepository.findByFileHash(fileHash);
            if (!existingFileOpt.isPresent()) {
                fileUpload = createNewFileUpload(filename, fileHash, username);
            } else {
                fileUpload = handleExistingFile(existingFileOpt.get(), username);
            }

            JobExecution job = jobExecutionService.createJob(fileUpload, username);
            var jobId = job.getJobId();
            var jobStatus = job.getStatus();

            String filePath = fileStorageService.storeFile(file, jobId);
            job.setFilePath(filePath);

            log.info("File upload initiated for '{}' (ID: {}, Job ID: {})",
                    filename, fileUpload.getId(), jobId);

            var jobStatusDTO = JobStatusDTO.builder()
                    .jobId(jobId)
                    .status(jobStatus)
                    .build();

            log.info("Publishing event `JobCreatedEvent` for job with id: {}", jobId);

            eventPublisher.publish(new JobCreatedEvent(jobStatusDTO));

            return new FileUploadJobDTO(
                    fileUpload.getFilename(),
                    fileUpload.getFileHash(),
                    job.getJobId(),
                    job.getStatus()
            );

        } catch (DuplicateFileException | FileValidationException | FileProcessingException ex) {
            log.warn("Handled exception during file upload for '{}': {}", filename, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error processing file '{}': {}", filename, ex.getMessage(), ex);
            throw new FileProcessingException("Could not process file: " + ex.getMessage(), ex);
        }
    }

    /**
     * Crea un nuovo record di caricamento file nel database.
     *
     * Gestisce anche la possibile race condition che può verificarsi quando due utenti
     * tentano di caricare lo stesso file contemporaneamente, causando una violazione
     * del vincolo di unicità sull'hash del file nel database.
     *
     * @param filename Il nome del file caricato
     * @param fileHash L'hash del file, usato per rilevare duplicati
     * @param username L'utente che sta caricando il file
     * @return L'oggetto FileUpload salvato
     * @throws DuplicateFileException se un altro utente ha caricato lo stesso file contemporaneamente
     */
    private FileUpload createNewFileUpload(String filename, String fileHash, String username) {
        log.info("Creating new file upload record for '{}'", filename);

        var fileUpload = FileUpload.builder()
                .filename(filename)
                .uploadDate(LocalDateTime.now())
                .uploadedBy(username)
                .status(FileUploadStatus.PENDING)
                .fileHash(fileHash)
                .build();

        try {
            return fileUploadRepository.save(fileUpload);
        } catch (DataIntegrityViolationException e) {
            // Gestione della race condition
            if (e.getCause() instanceof ConstraintViolationException) {
                log.warn("Race condition detected while saving file with hash: {}", fileHash);
                throw new DuplicateFileException("File was uploaded by another user in the meantime");
            }
            log.error("Database error while saving file '{}'", filename, e);
            throw e;
        }
    }

    /**
     * Gestisce un file esistente che è stato precedentemente caricato nel sistema.
     *
     * Determina se un file già presente nel database può essere rielaborato in base al suo stato.
     * Lancia delle eccezioni se il file è già stato elaborato o è in fase di elaborazione,
     * altrimenti aggiorna lo stato del file per consentirne la rielaborazione.
     *
     * @param existingFile L'oggetto FileUpload esistente trovato nel database
     * @param username L'utente che sta tentando di caricare il file
     * @return L'oggetto FileUpload aggiornato
     * @throws DuplicateFileException se il file è già stato elaborato con successo
     * @throws FileProcessingException se il file è attualmente in elaborazione
     */
    private FileUpload handleExistingFile(FileUpload existingFile, String username) {
        log.info("Found existing file with same hash. ID: {}, Status: {}, Upload Date: {}, User: {}",
                existingFile.getId(), existingFile.getStatus(), existingFile.getUploadDate(),
                existingFile.getUploadedBy());

        if (!existingFile.canBeReprocessed()) {

            var latestJob = existingFile.getJobExecutions()
                    .stream()
                    .max(Comparator.comparing(JobExecution::getStartTime))
                    .orElseThrow(() -> new RuntimeException("Cannot find a job execution for file: " + existingFile.getId()));

            if (FileUploadStatus.COMPLETED.equals(existingFile.getStatus())) {
                throw new DuplicateFileException("File has already been uploaded and processed (File hash: " +
                        existingFile.getFileHash() + ", Job ID: " + latestJob.getJobId() + " Upload Date: " + existingFile.getUploadDate() +
                        ", User: " + existingFile.getUploadedBy() + ")");
            } else if (FileUploadStatus.PROCESSING.equals(existingFile.getStatus()) ||
                    FileUploadStatus.PENDING.equals(existingFile.getStatus())) {
                throw new FileProcessingException("File is currently being processed (File hash: " +
                        existingFile.getFileHash() + ", Job ID: " + latestJob.getJobId() + " Upload Date: " + existingFile.getUploadDate() +
                        ", User: " + existingFile.getUploadedBy() + ")");
            }
        }

        log.info("Existing file '{}' can be reprocessed. Status: {}",
                existingFile.getFilename(), existingFile.getStatus());

        existingFile.setStatus(FileUploadStatus.PENDING);
        existingFile.setUploadDate(LocalDateTime.now());
        existingFile.setUploadedBy(username);
        return fileUploadRepository.save(existingFile);
    }

    @Transactional(readOnly = true)
    public JobStatusDTO getJobStatus(String jobId) {
        JobExecution job = jobExecutionService.getJobExecutionById(jobId);
        FileUpload fileUpload = job.getFileUpload();
        if (fileUpload == null) {
            new ResourceNotFoundException("FileUpload not found for job with id: " + jobId);
        }
        return buildJobStatusDTO(job, fileUpload);
    }

    @Transactional(readOnly = true)
    public Page<JobStatusDTO> getJobsByFileHash(String hash, Pageable pageable) {

        FileUpload fileUpload = fileUploadRepository.findByFileHash(hash)
                .orElseThrow(() -> new ResourceNotFoundException("File with hash " + hash + " not found"));

        Page<JobExecution> jobs = jobExecutionService.getJobsByFileHash(hash, pageable);

        return jobs.map(job -> buildJobStatusDTO(job, fileUpload));
    }

    private static JobStatusDTO buildJobStatusDTO(JobExecution job, FileUpload fileUpload) {
        return JobStatusDTO.builder()
                .jobId(job.getJobId())
                .status(job.getStatus())
                .startTime(job.getStartTime())
                .endTime(job.getEndTime())
                .fileHash(fileUpload.getFileHash())
                .filename(fileUpload.getFilename())
                .totalRecords(fileUpload.getTotalRecords())
                .validRecords(fileUpload.getValidRecords())
                .invalidRecords(fileUpload.getInvalidRecords())
                .errorMessage(job.getErrorMessage())
                .build();
    }

    @Transactional(readOnly = true)
    public FileUploadDTO findById(Long id) {
        return fileUploadRepository.findById(id)
                .map(fileUploadMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("FileUpload not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public FileUploadSummaryDTO findByHashWithSummary(String hash) {
        return fileUploadRepository.findByFileHash(hash)
                .map(fileUploadMapper::toSummaryDto)
                .orElseThrow(() -> new ResourceNotFoundException("FileUpload not found with hash: " + hash));
    }

}
