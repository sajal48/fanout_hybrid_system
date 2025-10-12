package com.twitter.feed.common.exception;

/**
 * Exception thrown when feed generation fails.
 * Follows Single Responsibility Principle.
 */
public class FeedGenerationException extends RuntimeException {

    public FeedGenerationException(String message) {
        super(message);
    }

    public FeedGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    public FeedGenerationException(Long userId, Throwable cause) {
        super(String.format("Failed to generate feed for user: %d", userId), cause);
    }
}
