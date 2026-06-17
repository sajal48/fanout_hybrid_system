package com.twitter.feed.feed.service;

import com.twitter.feed.common.exception.FeedGenerationException;
import com.twitter.feed.config.FeedConfig;
import com.twitter.feed.feed.model.FeedItem;
import com.twitter.feed.feed.repository.FeedCacheRepository;
import com.twitter.feed.post.model.CelebrityPost;
import com.twitter.feed.post.model.Post;
import com.twitter.feed.post.repository.CelebrityPostRepository;
import com.twitter.feed.post.repository.PostRepository;
import com.twitter.feed.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of FeedService.
 * Retrieves and merges feeds from multiple sources (Redis cache + Cassandra).
 *
 * Follows Single Responsibility Principle - manages feed retrieval.
 * Follows Dependency Inversion Principle - depends on abstractions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedServiceImpl implements FeedService {

    private final FeedCacheRepository feedCacheRepository;
    private final CelebrityPostRepository celebrityPostRepository;
    private final PostRepository postRepository;
    private final UserService userService;
    private final FeedMerger feedMerger;
    private final FeedConfig feedConfig;

    @Override
    public List<FeedItem> getUserFeed(Long userId, int limit) {
        log.info("Generating feed for user {} with limit {}", userId, limit);

        try {
            long startTime = System.currentTimeMillis();

            // Step 1: Get cached feed items (from fan-out on write)
            List<FeedItem> cachedFeedItems = getCachedFeedItems(userId, limit);

            // Step 2: Get celebrity posts (from fan-out on read)
            List<FeedItem> celebrityFeedItems = getCelebrityFeedItems(userId, limit);

            // Step 3: Merge and sort by timestamp
            List<FeedItem> mergedFeed = feedMerger.mergeFeed(
                    cachedFeedItems,
                    celebrityFeedItems,
                    Math.min(limit, feedConfig.getMaxFeedSize())
            );

            long duration = System.currentTimeMillis() - startTime;
            log.info("Generated feed for user {} with {} items in {}ms",
                    userId, mergedFeed.size(), duration);

            return mergedFeed;

        } catch (Exception e) {
            log.error("Failed to generate feed for user {}", userId, e);
            throw new FeedGenerationException(userId, e);
        }
    }

    @Override
    public List<FeedItem> getUserFeed(Long userId, int limit, int offset) {
        // Simple implementation - get more items and skip offset
        List<FeedItem> allItems = getUserFeed(userId, limit + offset);

        return allItems.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get cached feed items from Redis (fan-out on write).
     * OPTIMIZATION: Uses batch queries instead of N+1 pattern.
     */
    private List<FeedItem> getCachedFeedItems(Long userId, int limit) {
        try {
            Set<String> postIds = feedCacheRepository.getRecentFeed(userId, limit);

            if (postIds == null || postIds.isEmpty()) {
                log.debug("No cached feed items for user {}", userId);
                return new ArrayList<>();
            }

            // Convert post ID strings to UUIDs
            List<UUID> postUuids = postIds.stream()
                    .map(UUID::fromString)
                    .collect(Collectors.toList());

            // OPTIMIZATION: Batch fetch posts instead of N individual queries
            List<Post> posts = postRepository.findByIds(postUuids);

            // Convert to feed items and filter out null entries
            List<FeedItem> feedItems = posts.stream()
                    .filter(post -> post != null)
                    .map(post -> convertToFeedItem(post, false))
                    .collect(Collectors.toList());

            log.debug("Retrieved {} cached feed items for user {} with {} batch query (was {} individual queries)",
                    feedItems.size(), userId, 1, postUuids.size());

            return feedItems;

        } catch (Exception e) {
            log.error("Failed to get cached feed for user {}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * Get celebrity feed items from Cassandra (fan-out on read).
     * OPTIMIZATION: Uses batch queries instead of N+1 pattern.
     */
    private List<FeedItem> getCelebrityFeedItems(Long userId, int limit) {
        try {
            // Get celebrity users that this user follows
            List<Long> celebrityIds = userService.getCelebrityFollowingIds(userId);

            if (celebrityIds.isEmpty()) {
                log.debug("User {} doesn't follow any celebrities", userId);
                return new ArrayList<>();
            }

            log.debug("User {} follows {} celebrities", userId, celebrityIds.size());

            // OPTIMIZATION: Batch fetch celebrity posts instead of N individual queries
            List<CelebrityPost> posts = celebrityPostRepository.findRecentByUserIds(celebrityIds, limit);

            // Convert to feed items
            List<FeedItem> celebrityFeedItems = posts.stream()
                    .filter(post -> post != null)
                    .map(post -> convertToFeedItem(post, true))
                    .collect(Collectors.toList());

            log.debug("Retrieved {} celebrity feed items for user {} with {} batch query (was {} individual queries)",
                    celebrityFeedItems.size(), userId, 1, celebrityIds.size());

            return celebrityFeedItems;

        } catch (Exception e) {
            log.error("Failed to get celebrity feed for user {}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * Convert Post to FeedItem.
     */
    private FeedItem convertToFeedItem(Post post, boolean isCelebrity) {
        return FeedItem.builder()
                .postId(post.getPost_id())
                .authorId(post.getUserId())
                .authorUsername(post.getUsername())
                .content(post.getContent())
                .mediaUrls(post.getMediaUrls())
                .hashtags(post.getHashtags())
                .likeCount(post.getLikeCount())
                .retweetCount(post.getRetweetCount())
                .replyCount(post.getReplyCount())
                .createdAt(post.getCreatedAt())
                .isCelebrityPost(isCelebrity)
                .build();
    }

    /**
     * Convert CelebrityPost to FeedItem.
     */
    private FeedItem convertToFeedItem(CelebrityPost post, boolean isCelebrity) {
        return FeedItem.builder()
                .postId(post.getPostId())
                .authorId(post.getUserId())
                .authorUsername(post.getUsername())
                .content(post.getContent())
                .mediaUrls(post.getMediaUrls())
                .hashtags(post.getHashtags())
                .likeCount(post.getLikeCount())
                .retweetCount(post.getRetweetCount())
                .replyCount(post.getReplyCount())
                .createdAt(post.getCreatedAt())
                .isCelebrityPost(isCelebrity)
                .build();
    }
}
