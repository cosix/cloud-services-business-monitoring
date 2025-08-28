package com.cimparato.csbm.config.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
public class FileProcessingTaskExecutor extends ThreadPoolTaskExecutor {

    // Record wrapper per tenere traccia del jobId
    private record JobAwareRunnable(Runnable task, String jobId) implements Runnable {
        @Override
        public void run() {
            task.run();
        }
    }

    /**
     * Configura l'executor per l'elaborazione asincrona dei file.
     *
     * - corePoolSize: numero di thread mantenuti attivi
     * - maxPoolSize: numero massimo di thread che possono essere creati
     * - queueCapacity: dimensione della coda di task prima di creare nuovi thread
     * - rejectedExecutionHandler: Strategia quando la coda è piena e tutti i thread sono occupati.
     *      CallerRunsPolicy fa eseguire il task nel thread chiamante se la coda è piena
     * - keepAliveSeconds: Tempo in secondi dopo il quale i thread in eccesso rispetto al core vengono terminati quando
     *      sono inattivi (60 secondi)
     * - setRejectedExecutionHandler: Policy eseguita quando un task viene rifiutato perché il pool di thread è saturo
     *      e la coda è piena.
     */
    public FileProcessingTaskExecutor() {

        setCorePoolSize(2);
        setMaxPoolSize(5);
        setQueueCapacity(25);
        setThreadNamePrefix("file-proc-");
        setKeepAliveSeconds(60);

        setRejectedExecutionHandler((r, executor) -> {
            String jobId = "unknown";
            if (r instanceof JobAwareRunnable) {
                jobId = ((JobAwareRunnable) r).jobId();
            }
            log.error("File processing task rejected for job {}. Thread pool saturated.", jobId);
            throw new RejectedExecutionException("File processing task rejected due to system overload");
        });

        initialize();
    }

    // Metodo per eseguire task tenendo traccia del jobId
    public void executeWithJobId(Runnable task, String jobId) {
        execute(new JobAwareRunnable(task, jobId));
    }
}
