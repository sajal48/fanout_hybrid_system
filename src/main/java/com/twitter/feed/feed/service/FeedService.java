package com.twitter.feed.feed.service;

import com.twitter.feed.feed.model.FeedItem;

import java.util.List;

/**
 * Service interface for feed operations.
 * Follows Interface Segregation Principle - focused on feed operations.
 * Follows Dependency Inversion Principle - abstracts implementation details.
 */
public interface FeedService {

    /**
     * Get user's feed by merging cached posts and celebrity posts.
     *
     * @param userId the user ID
     * @param limit maximum number of items to return
     * @return list of feed items
     */
    List<FeedItem> getUserFeed(Long userId, int limit);

    /**
     * Get user's feed with pagination.
     *
     * @param userId the user ID
     * @param limit maximum number of items
     * @param offset offset for pagination
     * @return list of feed items
     */
    List<FeedItem> getUserFeed(Long userId, int limit, int offset);
}
