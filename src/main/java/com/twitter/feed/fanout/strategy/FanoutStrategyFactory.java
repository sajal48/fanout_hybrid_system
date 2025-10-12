package com.twitter.feed.fanout.strategy;

import com.twitter.feed.config.FeedConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Factory for selecting appropriate fanout strategy based on follower count.
 *
 * Follows Open/Closed Principle - strategies can be added without modifying this class.
 * Follows Dependency Inversion Principle - depends on FanoutStrategy interface.
 * Follows Single Responsibility Principle - only handles strategy selection.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FanoutStrategyFactory {

    private final FanoutOnWriteStrategy fanoutOnWriteStrategy;
    private final FanoutOnReadStrategy fanoutOnReadStrategy;
    private final FeedConfig feedConfig;

    /**
     * Get appropriate fanout strategy based on follower count.
     *
     * @param followerCount the number of followers
     * @return the appropriate fanout strategy
     */
    public FanoutStrategy getStrategy(long followerCount) {
        if (followerCount < feedConfig.getCelebrityThreshold()) {
            log.debug("Selected fan-out on write strategy for {} followers", followerCount);
            return fanoutOnWriteStrategy;
        } else {
            log.debug("Selected fan-out on read strategy for {} followers (celebrity)", followerCount);
            return fanoutOnReadStrategy;
        }
    }

    /**
     * Check if user should use fan-out on read strategy (celebrity).
     *
     * @param followerCount the number of followers
     * @return true if celebrity (fan-out on read)
     */
    public boolean shouldUseFanoutOnRead(long followerCount) {
        return followerCount >= feedConfig.getCelebrityThreshold();
    }
}
