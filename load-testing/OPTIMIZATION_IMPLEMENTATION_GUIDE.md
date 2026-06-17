# Feed Performance Optimization Implementation Guide

**Last Updated:** June 17, 2026  
**Priority:** CRITICAL  
**Estimated Completion:** 1-2 weeks

---

## Quick Start: Critical Code Changes

### 1. Batch Cassandra Reads (HIGHEST PRIORITY)

**File:** `src/main/java/com/twitter/feed/post/repository/PostRepository.java`

Add batch read method:
```java
@Repository
public interface PostRepository extends CassandraRepository<Post, UUID> {
    
    List<Post> findByUserId(Long userId);
    
    List<Post> findByUserIdAndCreatedAtAfter(Long userId, Instant since);
    
    List<Post> findByHashtagsContaining(String hashtag);
    
    // NEW: Batch read method
    @Query("SELECT * FROM posts WHERE post_id IN ?0")
    List<Post> findByPostIdIn(List<UUID> postIds);
}
```

**File:** `src/main/java/com/twitter/feed/feed/service/FeedServiceImpl.java`

Replace `getCachedFeedItems` method:
```java
/**
 * Get cached feed items from Redis (fan-out on write).
 * OPTIMIZED: Uses batch operations instead of per-post queries
 */
private List<FeedItem> getCachedFeedItems(Long userId, int limit) {
    try {
        Set<String> postIds = feedCacheRepository.getRecentFeed(userId, limit);

        if (postIds == null || postIds.isEmpty()) {
            log.debug("No cached feed items for user {}", userId);
            return new ArrayList<>();
        }

        // Convert to UUIDs
        List<UUID> postIdsList = postIds.stream()
            .map(UUID::fromString)
            .collect(Collectors.toList());

        // OPTIMIZATION 1: Batch Redis GET instead of per-post queries
        List<FeedItem> cachedItems = feedCacheRepository.getCachedPosts(postIdsList);
        Set<UUID> cachedIds = cachedItems.stream()
            .map(FeedItem::getPostId)
            .collect(Collectors.toSet());

        // OPTIMIZATION 2: Single batch Cassandra query for missing posts
        List<UUID> missingIds = postIdsList.stream()
            .filter(id -> !cachedIds.contains(id))
            .collect(Collectors.toList());

        if (!missingIds.isEmpty()) {
            // BEFORE: 20 individual queries (postRepository.findById in loop)
            // AFTER: 1 batch query
            List<Post> posts = postRepository.findByPostIdIn(missingIds);
            
            for (Post post : posts) {
                FeedItem feedItem = convertToFeedItem(post, false);
                cachedItems.add(feedItem);
                
                // Cache asynchronously to avoid blocking
                asyncCachePost(post.getPost_id(), feedItem);
            }
        }

        log.debug("Retrieved {} cached feed items for user {}", cachedItems.size(), userId);
        return cachedItems;

    } catch (Exception e) {
        log.error("Failed to get cached feed for user {}", userId, e);
        return new ArrayList<>();
    }
}

/**
 * Async cache operation - non-blocking
 */
private void asyncCachePost(UUID postId, FeedItem feedItem) {
    CompletableFuture.runAsync(() -> 
        feedCacheRepository.cachePost(postId, feedItem, feedConfig.getCacheTtlSeconds())
    );
}
```

---

### 2. Batch Celebrity Feed Queries (HIGH PRIORITY)

**File:** `src/main/java/com/twitter/feed/post/repository/CelebrityPostRepository.java`

Add batch method:
```java
@Repository
public interface CelebrityPostRepository extends CassandraRepository<CelebrityPost, UUID> {
    
    List<CelebrityPost> findRecentByUserId(Long userId, int limit);
    
    // NEW: Batch query for multiple celebrities
    @Query("SELECT * FROM celebrity_posts WHERE user_id IN ?0 LIMIT ?1 ALLOW FILTERING")
    List<CelebrityPost> findRecentByUserIds(List<Long> userIds, int limit);
}
```

**File:** `src/main/java/com/twitter/feed/feed/service/FeedServiceImpl.java`

Replace `getCelebrityFeedItems` method:
```java
/**
 * Get celebrity feed items from Cassandra (fan-out on read).
 * OPTIMIZED: Single batch query instead of per-celebrity queries
 */
private List<FeedItem> getCelebrityFeedItems(Long userId, int limit) {
    try {
        List<Long> celebrityIds = userService.getCelebrityFollowingIds(userId);

        if (celebrityIds.isEmpty()) {
            log.debug("User {} doesn't follow any celebrities", userId);
            return new ArrayList<>();
        }

        log.debug("User {} follows {} celebrities", userId, celebrityIds.size());

        // OPTIMIZATION: Single batch query for all celebrities
        // BEFORE: celebityIds.size() queries (e.g., 50 queries for 50 celebrities)
        // AFTER: 1 query
        List<CelebrityPost> posts = celebrityPostRepository.findRecentByUserIds(
            celebrityIds,
            limit
        );

        List<FeedItem> celebrityFeedItems = posts.stream()
            .map(post -> convertToFeedItem(post, true))
            .collect(Collectors.toList());

        log.debug("Retrieved {} celebrity feed items for user {}", 
            celebrityFeedItems.size(), userId);
        return celebrityFeedItems;

    } catch (Exception e) {
        log.error("Failed to get celebrity feed for user {}", userId, e);
        return new ArrayList<>();
    }
}
```

---

### 3. Batch Redis Operations (MEDIUM PRIORITY)

**File:** `src/main/java/com/twitter/feed/feed/repository/FeedCacheRepository.java`

Add batch method:
```java
/**
 * Get multiple cached posts in a single batch operation.
 * OPTIMIZATION: MGET instead of multiple GET operations
 */
public List<FeedItem> getCachedPosts(List<UUID> postIds) {
    if (postIds == null || postIds.isEmpty()) {
        return new ArrayList<>();
    }
    
    try {
        // OPTIMIZATION: Use Redis MGET (multiple get) instead of GET loop
        // BEFORE: 20 individual GET operations
        // AFTER: 1 MGET operation
        List<String> keys = postIds.stream()
            .map(this::getPostKey)
            .collect(Collectors.toList());
        
        List<FeedItem> items = feedItemRedisTemplate.opsForValue().multiGet(keys);
        
        // Filter out null values (cache misses)
        return items.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
            
    } catch (Exception e) {
        log.error("Failed to get cached posts", e);
        return new ArrayList<>();
    }
}

/**
 * Cache multiple posts at once (for batch operations)
 */
public void cachePostsBatch(Map<UUID, FeedItem> items, long ttlSeconds) {
    if (items == null || items.isEmpty()) {
        return;
    }
    
    try {
        for (Map.Entry<UUID, FeedItem> entry : items.entrySet()) {
            cachePost(entry.getKey(), entry.getValue(), ttlSeconds);
        }
    } catch (Exception e) {
        log.error("Failed to batch cache posts", e);
    }
}
```

---

### 4. Configuration Changes (CRITICAL)

**File:** `application.yml` or `application.properties`

```yaml
spring:
  cassandra:
    connection:
      connect-timeout-ms: 5000
      init-query-timeout: 5000
      
    pool:
      # BEFORE: Default 8 core, 16 max connections
      # AFTER: Increased to handle load
      core-connections: 32        # Increase from 8
      max-connections: 128        # Increase from 16
      idle-timeout-seconds: 600   # 10 minutes
      validation-query: "SELECT 1 FROM system.local"
      
    request:
      timeout: 10000              # 10 second timeout (from 5)
      consistency: "ONE"           # For read performance
      throttler:
        type: RATE_LIMITING_THROTTLER
        max-concurrent-requests: 2000
        max-queued-requests: 10000
        max-requests-per-second: 100000

server:
  tomcat:
    threads:
      max: 1000              # Increased from default 200
      min-spare: 100         # Maintain minimum threads
    max-connections: 20000
    max-http-header-size: 16KB
    max-http-post-size: 2MB
    
logging:
  level:
    com.twitter.feed: INFO
    org.springframework.data.cassandra: WARN
```

---

## Phase 1 Checklist (Week 1)

- [ ] Add `findByPostIdIn()` method to PostRepository
- [ ] Add `findRecentByUserIds()` method to CelebrityPostRepository  
- [ ] Add `getCachedPosts()` method to FeedCacheRepository
- [ ] Update `getCachedFeed
