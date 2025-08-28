package com.cimparato.csbm.config.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    /**
     * Configura lo scheduler per i task pianificati con @Scheduled.
     *
     * - poolSize: numero di thread dedicati ai task schedulati
     * - errorHandler: gestisce le eccezioni nei task schedulati
     * - waitForTasksToCompleteOnShutdown: attende il completamento dei task durante lo shutdown
     * - awaitTerminationSeconds: tempo massimo di attesa per il completamento dei task durante lo shutdown
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("scheduled-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setErrorHandler(t -> {
            Logger log = LoggerFactory.getLogger(this.getClass());
            StackTraceElement[] stackTrace = t.getStackTrace();
            String methodName = stackTrace.length > 0 ? stackTrace[0].getMethodName() : "unknown";
            log.error("Errore nell'esecuzione del task schedulato '{}': {}", methodName, t.getMessage(), t);
        });
        return scheduler;
    }

    /**
     * Configura uno scheduler dedicato per i retry di Kafka.
     */
    @Bean
    public TaskScheduler kafkaRetryScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Runtime.getRuntime().availableProcessors());
        scheduler.setThreadNamePrefix("kafka-retry-");
        scheduler.setDaemon(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.setErrorHandler(t -> {
            Logger log = LoggerFactory.getLogger(this.getClass());
            log.error("Errore durante l'esecuzione di un retry Kafka: {}", t.getMessage(), t);
        });
        return scheduler;
    }

    /**
     * Configura il registrar per utilizzare il taskScheduler per tutti i metodi @Scheduled.
     * Questo metodo viene chiamato automaticamente da Spring quando l'applicazione si avvia.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler());
    }

}
