package com.ondemandmonitoring.controlgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    @Bean(destroyMethod = "close")
    ExecutorService controlExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(name = "controlTaskExecutor")
    TaskExecutor controlTaskExecutor(ExecutorService controlExecutor) {
        return new TaskExecutorAdapter(controlExecutor);
    }
}
