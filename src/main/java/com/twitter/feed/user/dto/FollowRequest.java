package com.twitter.feed.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for follow/unfollow operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowRequest {

    @NotNull(message = "Follower user ID is required")
    private Long followerUserId;

    @NotNull(message = "Followed user ID is required")
    private Long followedUserId;
}
