package com.twitter.feed.fanout.strategy;

import com.twitter.feed.post.model.Post;

import java.util.List;

/**
 * Strategy interface for fanout operations.
 * Follows Open/Closed Principle - open for extension, closed for modification.
 * Follows Interface Segregation Principle - focused interface for fanout.
 *
 * Implementations:
 * - FanoutOnWriteStrategy: Pre-computes feeds for all followers (< 10K)
 * - FanoutOnReadStrategy: Stores in celebrity table only (>= 10K)
 */
public interface FanoutStrategy {

    /**
     * Execute fanout for a post.
     *
     * @param post the post to fanout
     * @param followerIds list of follower IDs
     */
    void executeFanout(Post post, List<Long> followerIds);

    /**
     * Get the name of the strategy for logging/monitoring.
     *
     * @return strategy name
     */
    String getStrategyName();
}
