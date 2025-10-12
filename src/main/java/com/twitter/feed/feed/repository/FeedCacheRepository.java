package com.twitter.feed.feed.repository;

import com.twitter.feed.feed.model.FeedItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Repository for feed cache operations using Redis.
 * Uses Redis Sorted Sets for efficient time-ordered feed storage.
 *
 * Follows Single Responsibility Principle - only handles feed cache operations.
 * Follows Dependency Inversion Principle - depends on RedisTemplate abstraction.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class FeedCacheRepository {

    private static final String FEED_KEY_PREFIX = "user_feed:";
    private static final String POST_KEY_PREFIX = "post:";

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Add post to user's feed cache.
     *
     * @param userId the user ID
     * @param postId the post ID
     * @param timestamp the post timestamp
     * @param ttlSeconds time-to-live in seconds
     */
    public void addToFeed(Long userId, UUID postId, Instant timestamp, long ttlSeconds) {
        String key = getFeedKey(userId);
        double score = timestamp.toEpochMilli();

        redisTemplate.opsForZSet().add(key, postId.toString(), score);
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);

        log.debug("Added post {} to feed for user {}", postId, userId);
    }

    /**
     * Get recent posts from user's feed cache.
     *
     * @param userId the user ID
     * @param limit maximum number of posts
     * @return set of post IDs ordered by timestamp (newest first)
     */
    public Set<String> getRecentFeed(Long userId, int limit) {
        String key = getFeedKey(userId);
        // ZREVRANGE gets highest scores first (newest posts)
        Set<Object> objects = redisTemplate.opsForZSet()
            .reverseRange(key, 0, limit - 1);

        Set<String> postIds = objects != null
            ? objects.stream().map(Object::toString).collect(java.util.stream.Collectors.toSet())
            : java.util.Collections.emptySet();

        log.debug("Retrieved {} posts from feed cache for user {}", postIds.size(), userId);

        return postIds;
    }

    /**
     * Get posts from feed after a certain timestamp.
     *
     * @param userId the user ID
     * @param since timestamp to filter from
     * @param limit maximum number of posts
     * @return set of post IDs
     */
    public Set<String> getFeedAfter(Long userId, Instant since, int limit) {
        String key = getFeedKey(userId);
        double minScore = since.toEpochMilli();

        Set<Object> objects = redisTemplate.opsForZSet()
            .reverseRangeByScore(key, minScore, Double.MAX_VALUE, 0, limit);

        Set<String> postIds = objects != null
            ? objects.stream().map(Object::toString).collect(java.util.stream.Collectors.toSet())
            : java.util.Collections.emptySet();

        log.debug("Retrieved {} posts after {} from feed cache for user {}",
                  postIds.size(), since, userId);

        return postIds;
    }

    /**
     * Remove post from user's feed.
     *
     * @param userId the user ID
     * @param postId the post ID
     */
    public void removeFromFeed(Long userId, UUID postId) {
        String key = getFeedKey(userId);
        redisTemplate.opsForZSet().remove(key, postId.toString());

        log.debug("Removed post {} from feed for user {}", postId, userId);
    }

    /**
     * Clear entire feed for a user.
     *
     * @param userId the user ID
     */
    public void clearFeed(Long userId) {
        String key = getFeedKey(userId);
        redisTemplate.delete(key);

        log.debug("Cleared feed cache for user {}", userId);
    }

    /**
     * Get feed size for a user.
     *
     * @param userId the user ID
     * @return number of posts in cache
     */
    public Long getFeedSize(Long userId) {
        String key = getFeedKey(userId);
        return redisTemplate.opsForZSet().size(key);
    }

    /**
     * Cache post data.
     *
     * @param postId the post ID
     * @param feedItem the feed item data
     * @param ttlSeconds time-to-live in seconds
     */
    public void cachePost(UUID postId, FeedItem feedItem, long ttlSeconds) {
        String key = getPostKey(postId);
        redisTemplate.opsForValue().set(key, feedItem, ttlSeconds, TimeUnit.SECONDS);

        log.debug("Cached post data for {}", postId);
    }

    /**
     * Get cached post data.
     *
     * @param postId the post ID
     * @return feed item or null if not cached
     */
    public FeedItem getCachedPost(UUID postId) {
        String key = getPostKey(postId);
        Object value = redisTemplate.opsForValue().get(key);

        return value != null ? (FeedItem) value : null;
    }

    /**
     * Remove cached post data.
     *
     * @param postId the post ID
     */
    public void removeCachedPost(UUID postId) {
        String key = getPostKey(postId);
        redisTemplate.delete(key);

        log.debug("Removed cached post data for {}", postId);
    }

    /**
     * Check if feed exists for user.
     *
     * @param userId the user ID
     * @return true if feed exists
     */
    public boolean hasFeed(Long userId) {
        String key = getFeedKey(userId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private String getFeedKey(Long userId) {
        return FEED_KEY_PREFIX + userId;
    }

    private String getPostKey(UUID postId) {
        return POST_KEY_PREFIX + postId.toString();
    }
}
