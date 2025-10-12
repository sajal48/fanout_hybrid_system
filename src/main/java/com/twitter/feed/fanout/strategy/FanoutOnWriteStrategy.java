package com.twitter.feed.fanout.strategy;

import com.twitter.feed.config.FeedConfig;
import com.twitter.feed.feed.repository.FeedCacheRepository;
import com.twitter.feed.post.model.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Fan-out on Write strategy implementation with async execution.
 * Pre-computes feeds for all followers by pushing posts to their Redis caches asynchronously.
 *
 * Used for regular users (< 10K followers).
 * Optimizes for fast read performance with async write operations.
 *
 * Follows Single Responsibility Principle - handles write fanout only.
 * Follows Open/Closed Principle - implements FanoutStrategy interface.
 * Uses async execution with batching for improved performance.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FanoutOnWriteStrategy implements FanoutStrategy {

    private final FeedCacheRepository feedCacheRepository;
    private final FeedConfig feedConfig;

    /**
     * Synchronous entry point - delegates to async execution.
     * Returns immediately after scheduling async tasks.
     */
    @Override
    public void executeFanout(Post post, List<Long> followerIds) {
        if (followerIds == null || followerIds.isEmpty()) {
            log.debug("No followers to fanout for post {}", post.getPostId());
            return;
        }

        log.info("Scheduling async fan-out on write for post {} to {} followers",
                post.getPostId(), followerIds.size());

        // Execute fanout asynchronously - method returns immediately
        executeFanoutAsync(post, followerIds);

        log.debug("Fan-out scheduled for post {} - processing in background", post.getPostId());
    }

    /**
     * Asynchronous fanout execution with batching.
     * Processes followers in batches to optimize Redis operations.
     *
     * @param post the post to fanout
     * @param followerIds list of follower IDs
     */
    @Async("fanoutExecutor")
    public CompletableFuture<Void> executeFanoutAsync(Post post, List<Long> followerIds) {
        long startTime = System.currentTimeMillis();

        try {
            // Batch size from config, default to 100
            int batchSize = feedConfig.getFanout().getBatchSize();
            List<CompletableFuture<Void>> batchFutures = new ArrayList<>();

            // Split followers into batches
            for (int i = 0; i < followerIds.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, followerIds.size());
                List<Long> batch = followerIds.subList(i, endIndex);

                // Process each batch asynchronously
                CompletableFuture<Void> batchFuture = processBatch(post, batch);
                batchFutures.add(batchFuture);
            }

            // Wait for all batches to complete
            CompletableFuture<Void> allBatches = CompletableFuture.allOf(
                    batchFutures.toArray(new CompletableFuture[0])
            );

            allBatches.join();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Completed async fan-out on write for post {} to {} followers in {}ms",
                    post.getPostId(), followerIds.size(), duration);

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("Failed to complete fan-out on write for post {}", post.getPostId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Process a batch of followers.
     * Writes to Redis for each follower in the batch.
     */
    @Async("fanoutExecutor")
    protected CompletableFuture<Void> processBatch(Post post, List<Long> followerBatch) {
        log.debug("Processing batch of {} followers for post {}", followerBatch.size(), post.getPostId());

        for (Long followerId : followerBatch) {
            try {
                feedCacheRepository.addToFeed(
                        followerId,
                        post.getPostId(),
                        post.getCreatedAt(),
                        feedConfig.getCacheTtlSeconds()
                );
            } catch (Exception e) {
                log.error("Failed to add post {} to follower {} feed",
                        post.getPostId(), followerId, e);
                // Continue with other followers even if one fails
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String getStrategyName() {
        return "FAN_OUT_ON_WRITE_ASYNC";
    }
}
