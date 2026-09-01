package com.xufg.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异步线程池配置单元测试。
 */
class AsyncConfigTest {

    /**
     * 文档入库线程池队列满时必须快速失败，避免调用线程被长任务阻塞。
     */
    @Test
    void shouldRejectKbIngestTaskWhenQueueIsFull() {
        ThreadPoolTaskExecutor executor = new AsyncConfig().kbIngestExecutor();
        executor.initialize();

        assertEquals(2, executor.getCorePoolSize());
        assertEquals(4, executor.getMaxPoolSize());
        assertTrue(executor.getThreadPoolExecutor().getRejectedExecutionHandler()
                instanceof ThreadPoolExecutor.AbortPolicy);
        executor.shutdown();
    }

    /**
     * 问答历史使用独立线程池，参数符合轻量落库场景。
     */
    @Test
    void shouldCreateQaHistoryExecutor() {
        ThreadPoolTaskExecutor executor = new AsyncConfig().qaHistoryExecutor();

        executor.initialize();
        assertEquals(2, executor.getCorePoolSize());
        assertEquals(4, executor.getMaxPoolSize());
        assertTrue(executor.getThreadNamePrefix().startsWith("qa-history-"));
        executor.shutdown();
    }
}
