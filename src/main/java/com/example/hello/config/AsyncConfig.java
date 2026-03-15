package com.example.hello.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "resumeProcessingExecutor")
    public Executor resumeProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：根据CPU核数和LLM接口并发限流情况设置，在此设为 10
        executor.setCorePoolSize(10);
        // 最大线程数：队列满时额外启动的线程数
        executor.setMaxPoolSize(20);
        // 队列大小：大量并发上传时的缓冲队列
        executor.setQueueCapacity(100);
        // 线程名前缀：方便日志排查
        executor.setThreadNamePrefix("Resume-Thread-");
        // 拒绝策略：使用CallerRunsPolicy，由提交任务的线程自己去执行，防止任务丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
