package com.twitter.feed.post.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Post entity representing a post in the system.
 * Stored in Cassandra for high write throughput and scalability.
 *
 * Follows Single Responsibility Principle - only handles post data.
 */
@Table("posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @PrimaryKey
    @Column("post_id")
    @Builder.Default
    private UUID postId = UUID.randomUUID();

    @Column("user_id")
    private Long userId;

    @Column("username")
    private String username;

    @Column("content")
    private String content;

    @Column("media_urls")
    private List<String> mediaUrls;

    @Column("hashtags")
    private Set<String> hashtags;

    @Column("mentions")
    private Set<Long> mentions;

    @Column("like_count")
    @Builder.Default
    private Long likeCount = 0L;

    @Column("retweet_count")
    @Builder.Default
    private Long retweetCount = 0L;

    @Column("reply_count")
    @Builder.Default
    private Long replyCount = 0L;

    @Column("created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column("updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
