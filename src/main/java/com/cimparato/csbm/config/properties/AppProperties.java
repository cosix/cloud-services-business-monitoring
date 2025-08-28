package com.cimparato.csbm.config.properties;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private FileProcessing fileProcessing;
    private Notification notification;
    private Scheduling scheduling;
    
    @Data
    @Validated
    public static class FileProcessing {
        private @NotNull String uploadDir;
        private @NotNull @NotEmpty String[] allowedExtensions;
        private int batchSize;
    }

    // Notification properties
    @Data
    @Validated
    public static class Notification {
            private Rule rule;
    }
    
    @Data
    @Validated
    public static class Rule {
        ActiveServiceOlderThanNotificationRule activeServiceOlderThanNotificationRule;
        ExpiredServicesNotificationRule expiredServicesNotificationRule;
    }
    
    @Data
    @Validated
    public static class ActiveServiceOlderThanNotificationRule {
        int years;
        Email email;
    }

    @Data
    @Validated
    public static class Email {
        @NotNull String sender;
        @NotNull String recipient;
        @NotNull String subject;
        String content;
    }

    @Data
    @Validated
    public static class ExpiredServicesNotificationRule {
        int maxExpiredServicesCount;
        Alert alert;
    }

    @Data
    @Validated
    public static class Alert {
        String sender;
        String subject;
        String content;
    }



    // Async properties
    @Data
    @Validated
    public static class Scheduling {
        ThreadPoolTaskExecutor threadPoolTaskExecutor;
        TaskScheduler taskScheduler;
    }

    @Data
    @Validated
    public static class ThreadPoolTaskExecutor {
        FileProcessingTaskExecutor fileProcessingTaskExecutor;
    }

    @Data
    @Validated
    public static class FileProcessingTaskExecutor {
        int corePoolSize;
        int maxPoolSize;
        int queueCapacity;
        @NotNull String threadNamePrefix;
        int keepAliveSeconds;
    }

    @Data
    @Validated
    public static class TaskScheduler {
        int poolSize;
        @NotNull String threadNamePrefix;
        boolean waitForTasksToCompleteOnShutdown;
        int awaitTerminationSeconds;
        JobScheduling jobScheduling;
    }

    @Data
    @Validated
    public static class JobScheduling {
        @NotNull String failedJobsRetryCron;
        @NotNull String notificationProcessingCron;
        @NotNull String failedNotificationsRetryCron;
        @NotNull String reportSchedulerStatusCron;
    }

}
