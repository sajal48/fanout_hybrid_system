package com.twitter.feed.feed.controller;

import com.twitter.feed.common.dto.ApiResponse;
import com.twitter.feed.config.FeedConfig;
import com.twitter.feed.feed.dto.FeedResponse;
import com.twitter.feed.feed.model.FeedItem;
import com.twitter.feed.feed.service.FeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for feed operations.
 *
 * Endpoints:
 * - GET /api/v1/feed - Get user's feed
 *
 * Follows Single Responsibility Principle - handles HTTP layer only.
 * Follows Dependency Inversion Principle - depends on FeedService interface.
 */
@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
@Slf4j
public class FeedController {

    private final FeedService feedService;
    private final FeedConfig feedConfig;

    /**
     * Get user's feed with pagination.
     *
     * @param userId the user ID requesting the feed
     * @param limit maximum number of items to return (default: 20, max: 100)
     * @param offset offset for pagination (default: 0)
     * @return paginated feed response
     */
    @GetMapping
    public ResponseEntity<ApiResponse<FeedResponse>> getUserFeed(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        log.info("Fetching feed for user {} with limit={}, offset={}", userId, limit, offset);

        // Validate and cap limit
        int effectiveLimit = Math.min(limit, feedConfig.getMaxPageSize());
        if (effectiveLimit <= 0) {
            effectiveLimit = feedConfig.getDefaultPageSize();
        }

        // Get feed items
        List<FeedItem> feedItems;
        if (offset > 0) {
            feedItems = feedService.getUserFeed(userId, effectiveLimit, offset);
        } else {
            feedItems = feedService.getUserFeed(userId, effectiveLimit);
        }

        // Build response with pagination metadata
        FeedResponse response = FeedResponse.builder()
                .items(feedItems)
                .totalItems(feedItems.size())
                .limit(effectiveLimit)
                .offset(offset)
                .hasMore(feedItems.size() >= effectiveLimit)
                .build();

        log.info("Returning {} feed items for user {}", feedItems.size(), userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get user's feed with default pagination (first page).
     *
     * @param userId the user ID requesting the feed
     * @return feed response with default page size
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<FeedResponse>> getUserFeedByPath(
            @PathVariable Long userId) {

        log.info("Fetching feed for user {} (default pagination)", userId);

        int defaultLimit = feedConfig.getDefaultPageSize();
        List<FeedItem> feedItems = feedService.getUserFeed(userId, defaultLimit);

        FeedResponse response = FeedResponse.builder()
                .items(feedItems)
                .totalItems(feedItems.size())
                .limit(defaultLimit)
                .offset(0)
                .hasMore(feedItems.size() >= defaultLimit)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
