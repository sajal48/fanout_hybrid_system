package com.twitter.feed.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for user data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String bio;
    private String location;
    private String website;
    private Long followerCount;
    private Long followingCount;
    private Boolean isCelebrity;
    private Instant createdAt;
    private Instant lastLoginAt;
}
