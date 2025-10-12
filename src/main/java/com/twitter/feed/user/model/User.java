package com.twitter.feed.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * User entity representing a user in the system.
 * Stored in PostgreSQL for relational data and metadata.
 *
 * Follows Single Responsibility Principle - only handles user data.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_username", columnList = "username"),
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_is_celebrity", columnList = "isCelebrity")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 100)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(nullable = false)
    @Builder.Default
    private Long followerCount = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Long followingCount = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isCelebrity = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if user has celebrity status based on follower count.
     *
     * @param celebrityThreshold the threshold for celebrity status
     * @return true if user is celebrity
     */
    public boolean isCelebrity(long celebrityThreshold) {
        return this.followerCount >= celebrityThreshold;
    }
}
