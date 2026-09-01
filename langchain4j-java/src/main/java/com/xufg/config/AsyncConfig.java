package com.xufg.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 知识库文档与问答历史异步处理配置。
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    /**
     * 文档解析入库线程池；队列已满时快速失败，由业务层标记失败并返回用户明确提示。
     */
    @Bean("kbIngestExecutor")
    public ThreadPoolTaskExecutor kbIngestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("kb-ingest-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    /**
     * 问答历史独立线程池，避免长耗时文档解析阻塞问答落库。
     */
    @Bean("qaHistoryExecutor")
    public ThreadPoolTaskExecutor qaHistoryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("qa-history-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
