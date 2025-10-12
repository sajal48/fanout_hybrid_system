package com.twitter.feed.post.exception;

/**
 * Exception thrown when a post fails validation.
 * Follows Single Responsibility Principle.
 */
public class InvalidPostException extends RuntimeException {

    public InvalidPostException(String message) {
        super(message);
    }

    public InvalidPostException(String message, Throwable cause) {
        super(message, cause);
    }
}
