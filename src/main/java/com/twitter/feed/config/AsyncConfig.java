package com.twitter.feed.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async configuration for fanout operations.
 *
 * Configures separate thread pools for different async operations:
 * - fanoutExecutor: For fan-out on write operations (high concurrency)
 * - feedExecutor: For feed generation operations (moderate concurrency)
 *
 * Follows Single Responsibility Principle - manages async execution.
 */
@Configuration
@EnableAsync(proxyTargetClass = true)
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Thread pool for fanout operations.
     * High concurrency to handle large follower lists.
     */
    @Bean(name = "fanoutExecutor")
    public Executor fanoutExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size: number of threads to keep alive even if idle
        executor.setCorePoolSize(10);

        // Max pool size: maximum number of threads
        executor.setMaxPoolSize(50);

        // Queue capacity: number of tasks to queue before rejecting
        executor.setQueueCapacity(1000);

        // Thread name prefix for debugging
        executor.setThreadNamePrefix("fanout-");

        // Rejection policy: caller runs the task if pool is full
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Max wait time for shutdown
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("Initialized fanoutExecutor with core={}, max={}, queue={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());

        return executor;
    }

    /**
     * Thread pool for feed generation operations.
     * Moderate concurrency for batch processing.
     */
    @Bean(name = "feedExecutor")
    public Executor feedExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("feed-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("Initialized feedExecutor with core={}, max={}, queue={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());

        return executor;
    }

    /**
     * Default async executor.
     */
    @Override
    public Executor getAsyncExecutor() {
        return fanoutExecutor();
    }

    /**
     * Cache manager for user caching in async post service.
     * Uses in-memory concurrent map for fast lookups.
     */
    @Bean(name = "localCacheManager")
    public CacheManager localCacheManager() {
        return new ConcurrentMapCacheManager("users");
    }

    /**
     * Handle uncaught exceptions in async methods.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("Async execution error in method: {} with params: {}",
                    method.getName(), params, throwable);
        };
    }
}
