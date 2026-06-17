package com.twitter.feed.post.service;

import com.twitter.feed.post.model.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Async Post Service with write buffering and batching.
 * Queues post creation requests and processes them in batches
 * to optimize database writes.
 *
 * Benefits:
 * - Returns immediately (202 ACCEPTED) instead of blocking
 * - Batches writes to Cassandra for efficiency
 * - Reduces load on database connection pool
 * - Improves throughput under high concurrency
 *
 * Follows Single Responsibility Principle - handles async post creation only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncPostService {

    private final PostService postService;

    @Value("${feed.async-post.queue-size:10000}")
    private int queueSize;

    @Value("${feed.async-post.batch-size:50}")
    private int batchSize;

    @Value("${feed.async-post.batch-timeout-ms:1000}")
    private long batchTimeoutMs;

    private BlockingQueue<PostCreationTask> postQueue;
    private Thread batchProcessorThread;
    private volatile boolean running = true;

    /**
     * Initialize the batch processor thread.
     * Starts a background daemon thread that processes queued posts in batches.
     */
    @PostConstruct
    public void initialize() {
        this.postQueue = new LinkedBlockingQueue<>(queueSize);

        // Start batch processor thread
        batchProcessorThread = new Thread(this::processBatches, "AsyncPostBatchProcessor");
        batchProcessorThread.setDaemon(false);
        batchProcessorThread.start();

        log.info("AsyncPostService initialized with queue-size={}, batch-size={}, timeout={}ms",
                queueSize, batchSize, batchTimeoutMs);
    }

    /**
     * Queue a post for async creation.
     * Returns immediately with a provisional post ID.
     *
     * @param post the post to create
     * @return task with post ID and future for tracking
     * @throws IllegalStateException if queue is full
     */
    public PostCreationTask queuePostCreation(Post post) {
        // Generate post ID immediately
        if (post.getPost_id() == null) {
            post.setPost_id(UUID.randomUUID());
        }

        PostCreationTask task = new PostCreationTask(post);

        // Try to offer to queue (non-blocking)
        boolean offered = postQueue.offer(task);

        if (!offered) {
            log.warn("Post queue full, rejecting post {}", post.getPost_id());
            throw new IllegalStateException("Post creation queue is full. System under heavy load.");
        }

        log.debug("Post {} queued for async creation", post.getPost_id());
        return task;
    }

    /**
     * Batch processor thread main loop.
     * Continuously processes queued posts in batches.
     */
    private void processBatches() {
        List<PostCreationTask> batch = new ArrayList<>(batchSize);
        long lastProcessTime = System.currentTimeMillis();

        while (running) {
            try {
                // Wait for first item with timeout
                PostCreationTask task = postQueue.poll(100, TimeUnit.MILLISECONDS);

                if (task != null) {
                    batch.add(task);
                }

                // Check if we should process the batch
                long currentTime = System.currentTimeMillis();
                boolean batchFull = batch.size() >= batchSize;
                boolean timeoutExceeded = (currentTime - lastProcessTime) >= batchTimeoutMs;
                boolean shouldProcess = batchFull || (timeoutExceeded && !batch.isEmpty());

                if (shouldProcess) {
                    processBatch(batch);
                    batch.clear();
                    lastProcessTime = currentTime;
                }

            } catch (InterruptedException e) {
                log.warn("Batch processor interrupted", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in batch processor", e);
            }
        }

        // Process remaining items on shutdown
        if (!batch.isEmpty()) {
            log.info("Processing {} remaining posts on shutdown", batch.size());
            processBatch(batch);
        }

        log.info("AsyncPostService batch processor stopped");
    }

    /**
     * Process a batch of posts asynchronously.
     * Each post creation is executed in parallel.
     *
     * @param batch list of posts to process
     */
    @Async("fanoutExecutor")
    protected void processBatch(List<PostCreationTask> batch) {
        long startTime = System.currentTimeMillis();
        log.info("Processing batch of {} posts", batch.size());

        // Process posts in parallel using stream
        List<CompletableFuture<Void>> futures = batch.stream()
                .map(task -> CompletableFuture.runAsync(() -> processPostCreation(task)))
                .collect(Collectors.toList());

        // Wait for all posts in batch to complete
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );
            allFutures.join();

            long duration = System.currentTimeMillis() - startTime;
            long successCount = batch.stream().filter(PostCreationTask::isSuccess).count();
            long failureCount = batch.size() - successCount;

            log.info("Batch processing completed in {}ms: {} success, {} failures",
                    duration, successCount, failureCount);

        } catch (Exception e) {
            log.error("Error waiting for batch completion", e);
        }
    }

    /**
     * Process individual post creation.
     * Called within async execution.
     *
     * @param task the post creation task
     */
    private void processPostCreation(PostCreationTask task) {
        try {
            Post createdPost = postService.createPost(task.getPost());
            task.complete(createdPost);
            log.debug("Post {} created successfully", createdPost.getPost_id());
        } catch (Exception e) {
            log.error("Failed to create post {}", task.getPost().getPost_id(), e);
            task.completeExceptionally(e);
        }
    }

    /**
     * Shutdown the service gracefully.
     * Waits for remaining posts to be processed.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down AsyncPostService");
        running = false;

        try {
            if (batchProcessorThread != null && batchProcessorThread.isAlive()) {
                batchProcessorThread.join(30000); // Wait max 30 seconds
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for batch processor to stop", e);
            Thread.currentThread().interrupt();
        }

        log.info("AsyncPostService shutdown complete");
    }

    /**
     * Get current queue size (for monitoring).
     *
     * @return number of posts in queue
     */
    public int getQueueSize() {
        return postQueue.size();
    }

    /**
     * Get queue capacity (for monitoring).
     *
     * @return queue capacity
     */
    public int getQueueCapacity() {
        return queueSize;
    }

    /**
     * Task wrapper for post creation with future tracking.
     */
    @lombok.Data
    public static class PostCreationTask {
        private Post post;
        private CompletableFuture<Post> future;
        private long queuedTime;

        public PostCreationTask(Post post) {
            this.post = post;
            this.future = new CompletableFuture<>();
            this.queuedTime = System.currentTimeMillis();
        }

        public void complete(Post result) {
            future.complete(result);
        }

        public void completeExceptionally(Throwable ex) {
            future.completeExceptionally(ex);
        }

        public boolean isSuccess() {
            return future.isDone() && !future.isCompletedExceptionally();
        }

        public Post getResult() throws Exception {
            return future.get();
        }

        public long getQueueWaitTime() {
            return System.currentTimeMillis() - queuedTime;
        }
    }
}
