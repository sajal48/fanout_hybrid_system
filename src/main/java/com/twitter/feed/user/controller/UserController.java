package com.twitter.feed.user.controller;

import com.twitter.feed.common.dto.ApiResponse;
import com.twitter.feed.common.exception.ResourceNotFoundException;
import com.twitter.feed.user.dto.CreateUserRequest;
import com.twitter.feed.user.dto.FollowRequest;
import com.twitter.feed.user.dto.UserResponse;
import com.twitter.feed.user.model.User;
import com.twitter.feed.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for user operations.
 *
 * Endpoints:
 * - POST /api/v1/users - Create new user
 * - GET /api/v1/users/{userId} - Get user by ID
 * - GET /api/v1/users/username/{username} - Get user by username
 * - POST /api/v1/users/follow - Follow a user
 * - DELETE /api/v1/users/follow - Unfollow a user
 * - GET /api/v1/users/{userId}/followers - Get follower IDs
 * - GET /api/v1/users/{userId}/following - Get following IDs
 * - GET /api/v1/users/{userId}/celebrities - Get celebrity following IDs
 *
 * Follows Single Responsibility Principle - handles HTTP layer only.
 * Follows Dependency Inversion Principle - depends on UserService interface.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * Create a new user.
     *
     * @param request the user creation request
     * @return created user data
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {

        log.info("Creating new user with username: {}", request.getUsername());

        // Convert DTO to domain model
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .displayName(request.getDisplayName())
                .bio(request.getBio())
                .build();

        // Create user
        User createdUser = userService.createUser(user);

        // Convert to response DTO
        UserResponse response = convertToResponse(createdUser);

        log.info("User created successfully with ID: {}", createdUser.getId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", response));
    }

    /**
     * Get user by ID.
     *
     * @param userId the user ID
     * @return user data
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable Long userId) {

        log.debug("Retrieving user: {}", userId);

        User user = userService.getUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        UserResponse response = convertToResponse(user);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get user by username.
     *
     * @param username the username
     * @return user data
     */
    @GetMapping("/username/{username}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByUsername(
            @PathVariable String username) {

        log.debug("Retrieving user by username: {}", username);

        User user = userService.getUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        UserResponse response = convertToResponse(user);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Follow a user.
     *
     * @param request the follow request
     * @return success response
     */
    @PostMapping("/follow")
    public ResponseEntity<ApiResponse<Void>> followUser(
            @Valid @RequestBody FollowRequest request) {

        log.info("User {} following user {}",
                request.getFollowerUserId(), request.getFollowedUserId());

        // Validate that users are not the same
        if (request.getFollowerUserId().equals(request.getFollowedUserId())) {
            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error("Cannot follow yourself"));
        }

        userService.followUser(request.getFollowerUserId(), request.getFollowedUserId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Successfully followed user", null));
    }

    /**
     * Unfollow a user.
     *
     * @param request the unfollow request
     * @return success response
     */
    @DeleteMapping("/follow")
    public ResponseEntity<ApiResponse<Void>> unfollowUser(
            @Valid @RequestBody FollowRequest request) {

        log.info("User {} unfollowing user {}",
                request.getFollowerUserId(), request.getFollowedUserId());

        userService.unfollowUser(request.getFollowerUserId(), request.getFollowedUserId());

        return ResponseEntity.ok(ApiResponse.success("Successfully unfollowed user", null));
    }

    /**
     * Get follower IDs for a user.
     *
     * @param userId the user ID
     * @return list of follower IDs
     */
    @GetMapping("/{userId}/followers")
    public ResponseEntity<ApiResponse<List<Long>>> getFollowers(
            @PathVariable Long userId) {

        log.debug("Retrieving followers for user: {}", userId);

        List<Long> followerIds = userService.getFollowerIds(userId);

        return ResponseEntity.ok(ApiResponse.success(followerIds));
    }

    /**
     * Get following IDs for a user.
     *
     * @param userId the user ID
     * @return list of following IDs
     */
    @GetMapping("/{userId}/following")
    public ResponseEntity<ApiResponse<List<Long>>> getFollowing(
            @PathVariable Long userId) {

        log.debug("Retrieving following for user: {}", userId);

        List<Long> followingIds = userService.getFollowingIds(userId);

        return ResponseEntity.ok(ApiResponse.success(followingIds));
    }

    /**
     * Get celebrity users that a user follows.
     *
     * @param userId the user ID
     * @return list of celebrity user IDs
     */
    @GetMapping("/{userId}/celebrities")
    public ResponseEntity<ApiResponse<List<Long>>> getCelebrityFollowing(
            @PathVariable Long userId) {

        log.debug("Retrieving celebrity following for user: {}", userId);

        List<Long> celebrityIds = userService.getCelebrityFollowingIds(userId);

        return ResponseEntity.ok(ApiResponse.success(celebrityIds));
    }

    /**
     * Convert User entity to UserResponse DTO.
     */
    private UserResponse convertToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .bio(user.getBio())
                .location(null)  // Not yet implemented in User entity
                .website(null)   // Not yet implemented in User entity
                .followerCount(user.getFollowerCount())
                .followingCount(user.getFollowingCount())
                .isCelebrity(user.getIsCelebrity())
                .createdAt(user.getCreatedAt() != null ?
                    java.time.Instant.from(user.getCreatedAt().atZone(java.time.ZoneId.systemDefault())) : null)
                .lastLoginAt(null)  // Not yet implemented in User entity
                .build();
    }
}
