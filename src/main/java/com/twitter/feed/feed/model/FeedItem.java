package com.twitter.feed.feed.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * FeedItem model representing a single item in a user's feed.
 * This is a domain model, not a persistence entity.
 *
 * Follows Single Responsibility Principle - represents feed item data.
 * Used for merging posts from different sources (Redis cache and Cassandra).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedItem {

    private UUID postId;
    private Long authorId;
    private String authorUsername;
    private String content;
    private List<String> mediaUrls;
    private Set<String> hashtags;
    private Long likeCount;
    private Long retweetCount;
    private Long replyCount;
    private Instant createdAt;
    private boolean isCelebrityPost;
}
