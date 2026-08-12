package com.audioagent.config;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(AnalysisProperties.class)
public class AnalysisConfig {

    /**
     * 音频分析专用异步线程池。
     *
     * 拒绝策略选择 CallerRunsPolicy 的原因：
     * 分析任务是后台任务，允许偶尔由调用线程执行，
     * 这样在队列满时不会丢失任务，也不会抛出异常中断流程。
     * 后续替换为 RabbitMQ 时可以移除该策略。
     */
    @Bean("audioAnalysisExecutor")
    public ThreadPoolTaskExecutor audioAnalysisExecutor(AnalysisProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
