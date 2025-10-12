package com.twitter.feed.post.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CelebrityPost entity for posts by celebrity users.
 * Stored in Cassandra with user_id as partition key for efficient fan-out on read.
 *
 * Partitioned by user_id and clustered by created_at DESC for time-series queries.
 * Follows Single Responsibility Principle - optimized for celebrity post retrieval.
 */
@Table("celebrity_posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CelebrityPost {

    @PrimaryKeyColumn(name = "user_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private Long userId;

    @PrimaryKeyColumn(name = "created_at", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrimaryKeyColumn(name = "post_id", ordinal = 2, type = PrimaryKeyType.CLUSTERED)
    @Builder.Default
    private UUID postId = UUID.randomUUID();

    @Column("content")
    private String content;

    @Column("username")
    private String username;

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
}
