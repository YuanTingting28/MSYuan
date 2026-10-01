package com.yuan.msyuanbackend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class ThreadPoolConfig {

    @Value("${thread-pool.batch-add-question.core-size:20}")
    private int coreSize;

    @Value("${thread-pool.batch-add-question.max-size:50}")
    private int maxSize;

    @Value("${thread-pool.batch-add-question.queue-capacity:10000}")
    private int queueCapacity;

    @Value("${thread-pool.batch-add-question.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Value("${thread-pool.batch-add-question.thread-name-prefix:batch-add-question-}")
    private String threadNamePrefix;

    @Bean("batchAddQuestionExecutor")
    public ThreadPoolTaskExecutor batchAddQuestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}