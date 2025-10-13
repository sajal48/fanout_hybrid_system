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

    @PrimaryKey
    private CelebrityPostKey key;

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

    // Convenience methods for accessing primary key fields
    public Long getUserId() {
        return key != null ? key.getUserId() : null;
    }

    public void setUserId(Long userId) {
        if (key == null) {
            key = new CelebrityPostKey();
        }
        key.setUserId(userId);
    }

    public Instant getCreatedAt() {
        return key != null ? key.getCreatedAt() : null;
    }

    public void setCreatedAt(Instant createdAt) {
        if (key == null) {
            key = new CelebrityPostKey();
        }
        key.setCreatedAt(createdAt);
    }

    public UUID getPostId() {
        return key != null ? key.getPostId() : null;
    }

    public void setPostId(UUID postId) {
        if (key == null) {
            key = new CelebrityPostKey();
        }
        key.setPostId(postId);
    }
}
