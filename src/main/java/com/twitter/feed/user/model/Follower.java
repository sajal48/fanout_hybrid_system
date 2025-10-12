package com.twitter.feed.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Follower relationship entity representing follower/following connections.
 * Stored in PostgreSQL.
 *
 * Follows Single Responsibility Principle - only handles follower relationships.
 */
@Entity
@Table(name = "followers",
       uniqueConstraints = @UniqueConstraint(columnNames = {"followerId", "followingId"}),
       indexes = {
               @Index(name = "idx_followers_follower_id", columnList = "followerId"),
               @Index(name = "idx_followers_following_id", columnList = "followingId")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Follower {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long followerId;

    @Column(nullable = false)
    private Long followingId;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
