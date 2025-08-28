package com.cimparato.csbm.service.scheduled;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Classe per monitorare lo stato dei processi schedulati
 */
@Slf4j
public class ScheduledTasksMonitor {

    private final ThreadPoolTaskScheduler taskScheduler;
    private final Map<String, ThreadPoolTaskExecutor> executors;

    public ScheduledTasksMonitor(ThreadPoolTaskScheduler taskScheduler, Map<String, ThreadPoolTaskExecutor> executors) {
        this.taskScheduler = taskScheduler;
        this.executors = executors;
    }

    @Scheduled(cron = "${app.scheduling.task-scheduler.job-scheduling.report-scheduler-status-cron:0 0 */1 * * *}") // default ogni ora
    public void reportSchedulerStatusJob() {
        // Monitora il task scheduler
        ThreadPoolExecutor schedulerExecutor = taskScheduler.getScheduledThreadPoolExecutor();
        log.info("Scheduler status - Active: {}, Pool size: {}, Queue size: {}, Completed tasks: {}",
                schedulerExecutor.getActiveCount(),
                schedulerExecutor.getPoolSize(),
                schedulerExecutor.getQueue().size(),
                schedulerExecutor.getCompletedTaskCount());

        // Monitora tutti gli executor registrati
        executors.forEach((name, executor) -> {
            ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
            log.info("{} status - Active: {}, Pool size: {}, Queue size: {}, Completed tasks: {}",
                    name,
                    threadPoolExecutor.getActiveCount(),
                    threadPoolExecutor.getPoolSize(),
                    threadPoolExecutor.getQueue().size(),
                    threadPoolExecutor.getCompletedTaskCount());
        });
    }
}
