package com.twitter.feed.user.exception;

/**
 * Exception thrown when attempting to create a duplicate follower relationship.
 * Follows Single Responsibility Principle.
 */
public class DuplicateFollowerException extends RuntimeException {

    public DuplicateFollowerException(Long followerId, Long followingId) {
        super(String.format("User %d is already following user %d", followerId, followingId));
    }

    public DuplicateFollowerException(String message) {
        super(message);
    }
}
