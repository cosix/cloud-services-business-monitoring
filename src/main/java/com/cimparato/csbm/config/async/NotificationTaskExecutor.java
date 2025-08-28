package com.cimparato.csbm.config.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
public class NotificationTaskExecutor extends ThreadPoolTaskExecutor {

    // Record wrapper per tenere traccia del fileHash
    private record NotificationAwareRunnable(Runnable task, String fileHash) implements Runnable {
        @Override
        public void run() {
            task.run();
        }
    }

    /**
     * Configura l'executor per l'elaborazione asincrona delle notifiche.
     */
    public NotificationTaskExecutor() {
        setCorePoolSize(2);
        setMaxPoolSize(4);
        setQueueCapacity(25);
        setThreadNamePrefix("notif-proc-");
        setKeepAliveSeconds(60);

        setRejectedExecutionHandler((r, executor) -> {
            String fileHash = "unknown";
            if (r instanceof NotificationAwareRunnable) {
                fileHash = ((NotificationAwareRunnable) r).fileHash();
            }
            log.error("Notification processing task rejected for file {}. Thread pool saturated.", fileHash);
            throw new RejectedExecutionException("Notification processing rejected due to system overload");
        });

        initialize();
    }

    // Metodo per eseguire task tenendo traccia del fileHash
    public void executeWithFileHash(Runnable task, String fileHash) {
        execute(new NotificationAwareRunnable(task, fileHash));
    }
}
