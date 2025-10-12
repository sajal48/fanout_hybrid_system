package com.twitter.feed.user.service;

import com.twitter.feed.user.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for user operations.
 * Follows Interface Segregation Principle - focused on user operations.
 * Follows Dependency Inversion Principle - abstracts implementation details.
 */
public interface UserService {

    /**
     * Get user by ID.
     *
     * @param userId the user ID
     * @return optional user
     */
    Optional<User> getUserById(Long userId);

    /**
     * Get user by username.
     *
     * @param username the username
     * @return optional user
     */
    Optional<User> getUserByUsername(String username);

    /**
     * Create a new user.
     *
     * @param user the user to create
     * @return created user
     */
    User createUser(User user);

    /**
     * Update user.
     *
     * @param user the user to update
     * @return updated user
     */
    User updateUser(User user);

    /**
     * Delete user.
     *
     * @param userId the user ID
     */
    void deleteUser(Long userId);

    /**
     * Follow a user.
     *
     * @param followerId the follower user ID
     * @param followingId the user to follow
     */
    void followUser(Long followerId, Long followingId);

    /**
     * Unfollow a user.
     *
     * @param followerId the follower user ID
     * @param followingId the user to unfollow
     */
    void unfollowUser(Long followerId, Long followingId);

    /**
     * Get follower IDs for a user.
     *
     * @param userId the user ID
     * @return list of follower IDs
     */
    List<Long> getFollowerIds(Long userId);

    /**
     * Get following IDs for a user.
     *
     * @param userId the user ID
     * @return list of following IDs
     */
    List<Long> getFollowingIds(Long userId);

    /**
     * Get celebrity IDs that a user follows.
     *
     * @param userId the user ID
     * @return list of celebrity user IDs
     */
    List<Long> getCelebrityFollowingIds(Long userId);

    /**
     * Check if user is following another user.
     *
     * @param followerId the follower user ID
     * @param followingId the following user ID
     * @return true if following
     */
    boolean isFollowing(Long followerId, Long followingId);

    /**
     * Update celebrity status for a user.
     *
     * @param userId the user ID
     * @param isCelebrity the celebrity status
     */
    void updateCelebrityStatus(Long userId, boolean isCelebrity);
}
