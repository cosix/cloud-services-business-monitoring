package com.cimparato.csbm.service.scheduled;

import com.cimparato.csbm.domain.enumeration.JobStatus;
import com.cimparato.csbm.domain.event.JobCreatedEvent;
import com.cimparato.csbm.domain.model.JobExecution;
import com.cimparato.csbm.dto.jobexecution.JobStatusDTO;
import com.cimparato.csbm.service.file.FileProcessorService;
import com.cimparato.csbm.service.JobExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class FileProcessingRetryService {

    private final JobExecutionService jobExecutionService;
    private final FileProcessorService fileProcessorService;

    public FileProcessingRetryService(JobExecutionService jobExecutionService, FileProcessorService fileProcessorService) {
        this.jobExecutionService = jobExecutionService;
        this.fileProcessorService = fileProcessorService;
    }

    /**
     * Verifica e riprova l'elaborazione dei job falliti.
     *
     * Recupera tutti i job con stato FAILED, li imposta come PENDING e richiede
     * una nuova elaborazione tramite un evento JobCreatedEvent. Se il retry fallisce,
     * il job viene riportato allo stato FAILED con un messaggio appropriato.
     *
     * Questo meccanismo permette di recuperare automaticamente da errori temporanei
     * come sovraccarichi del sistema senza richiedere intervento manuale.
     */
    @Scheduled(cron = "${app.scheduling.task-scheduler.job-scheduling.failed-jobs-retry-cron:0 */10 * * * *}") // default ogni 10 minuti
    public void retryFailedJobsJob() {
        log.info("Checking for failed jobs to retry");

        List<JobExecution> failedJobs = jobExecutionService.getJobsWithStatus(JobStatus.FAILED);

        for (JobExecution job : failedJobs) {

            log.info("Retrying job: {}", job.getJobId());

            jobExecutionService.updateJobStatus(job.getJobId(), JobStatus.PENDING, null);

            try {

                JobStatusDTO jobStatusDTO = JobStatusDTO.builder()
                        .jobId(job.getJobId())
                        .status(JobStatus.PENDING)
                        .build();

                // Pubblica l'evento per riavviare l'elaborazione
                fileProcessorService.scheduleFileProcessing(new JobCreatedEvent(jobStatusDTO));

            } catch (Exception e) {

                var jobId = job.getJobId();
                log.error("Failed to retry job {}: {}", jobId, e.getMessage());

                try {
                    jobExecutionService.updateJobStatus(jobId, JobStatus.FAILED, "Retry failed: " + e.getMessage());
                } catch (Exception updateEx) {
                    log.error("Failed to update job status for {}: {}", jobId, updateEx.getMessage(), updateEx);
                }

            }

        }
    }
}
