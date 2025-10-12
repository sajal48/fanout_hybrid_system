package com.twitter.feed.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the feed system.
 * Externalized configuration following the Dependency Inversion Principle.
 */
@Configuration
@ConfigurationProperties(prefix = "feed")
@Data
public class FeedConfig {

    /**
     * Threshold for celebrity status (default: 10,000 followers)
     */
    private long celebrityThreshold = 10000;

    /**
     * Maximum number of posts to return in a feed
     */
    private int maxFeedSize = 100;

    /**
     * Cache time-to-live in seconds
     */
    private long cacheTtlSeconds = 3600;

    /**
     * Default page size for feed retrieval
     */
    private int defaultPageSize = 20;

    /**
     * Maximum page size allowed
     */
    private int maxPageSize = 100;

    /**
     * Fanout-specific configuration
     */
    private FanoutConfig fanout = new FanoutConfig();

    @Data
    public static class FanoutConfig {
        /**
         * Maximum followers for synchronous fanout
         */
        private int syncFanoutThreshold = 1000;

        /**
         * Batch size for async fanout operations
         */
        private int batchSize = 100;

        /**
         * Enable parallel batch processing
         */
        private boolean parallelBatchProcessing = true;
    }
}
