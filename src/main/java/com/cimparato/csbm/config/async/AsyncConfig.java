package com.cimparato.csbm.config.async;

import com.cimparato.csbm.config.properties.AppProperties;
import com.cimparato.csbm.service.scheduled.ScheduledTasksMonitor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class AsyncConfig {

    private final AppProperties appProperties;

    public AsyncConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean(name = "fileProcessingExecutor")
    public FileProcessingTaskExecutor fileProcessingExecutor() {
        return new FileProcessingTaskExecutor();
    }

    @Bean(name = "notificationExecutor")
    public NotificationTaskExecutor notificationExecutor() {
        return new NotificationTaskExecutor();
    }

    /**
     * Bean per monitorare lo stato dei processi schedulati
     */
    @Bean
    public ScheduledTasksMonitor scheduledTasksMonitor(
            TaskScheduler taskScheduler,
            FileProcessingTaskExecutor fileProcessingExecutor,
            NotificationTaskExecutor notificationExecutor) {

        Map<String, ThreadPoolTaskExecutor> executors = new HashMap<>();
        executors.put("File Processing Executor", fileProcessingExecutor);
        executors.put("Notification Executor", notificationExecutor);

        return new ScheduledTasksMonitor((ThreadPoolTaskScheduler) taskScheduler, executors);
    }
}
