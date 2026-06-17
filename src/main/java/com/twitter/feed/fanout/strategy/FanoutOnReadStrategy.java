package com.twitter.feed.fanout.strategy;

import com.twitter.feed.post.model.CelebrityPost;
import com.twitter.feed.post.model.CelebrityPostKey;
import com.twitter.feed.post.model.Post;
import com.twitter.feed.post.repository.CelebrityPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fan-out on Read strategy implementation.
 * Stores posts in celebrity-specific table for on-demand feed generation.
 *
 * Used for celebrity users (>= 10K followers).
 * Optimizes for write performance by avoiding immediate fanout to all followers.
 * Feeds are computed at read time by merging celebrity posts.
 *
 * Follows Single Responsibility Principle - handles read fanout only.
 * Follows Open/Closed Principle - implements FanoutStrategy interface.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FanoutOnReadStrategy implements FanoutStrategy {

    private final CelebrityPostRepository celebrityPostRepository;

    @Override
    public void executeFanout(Post post, List<Long> followerIds) {
        log.info("Executing fan-out on read for celebrity post {} from user {}",
                post.getPost_id(), post.getUserId());

        long startTime = System.currentTimeMillis();

        try {
            // Create the composite key first
            CelebrityPostKey key = new CelebrityPostKey(
                    post.getUserId(),
                    post.getCreatedAt(),
                    post.getPost_id()
            );

            // Store in celebrity posts table (partitioned by user_id)
            CelebrityPost celebrityPost = CelebrityPost.builder()
                    .key(key)
                    .content(post.getContent())
                    .username(post.getUsername())
                    .mediaUrls(post.getMediaUrls())
                    .hashtags(post.getHashtags())
                    .mentions(post.getMentions())
                    .likeCount(post.getLikeCount())
                    .retweetCount(post.getRetweetCount())
                    .replyCount(post.getReplyCount())
                    .build();

            celebrityPostRepository.save(celebrityPost);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Fan-out on read completed for post {} in {}ms (no immediate fanout)",
                    post.getPost_id(), duration);

        } catch (Exception e) {
            log.error("Failed to save celebrity post {} from user {}",
                    post.getPost_id(), post.getUserId(), e);
            throw new RuntimeException("Failed to execute fan-out on read", e);
        }
    }

    @Override
    public String getStrategyName() {
        return "FAN_OUT_ON_READ";
    }
}
