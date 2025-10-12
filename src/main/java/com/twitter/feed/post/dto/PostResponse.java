package com.twitter.feed.post.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Response DTO for post data.
 * Never expose entities directly in REST APIs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {

    private UUID postId;
    private Long userId;
    private String username;
    private String content;
    private List<String> mediaUrls;
    private Set<String> hashtags;
    private Long likeCount;
    private Long retweetCount;
    private Long replyCount;
    private Instant createdAt;
    private Instant updatedAt;
    private String fanoutStrategy;
}
