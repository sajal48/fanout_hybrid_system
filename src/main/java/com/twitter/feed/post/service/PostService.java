package com.twitter.feed.post.service;

import com.twitter.feed.post.model.Post;

import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for post operations.
 * Follows Interface Segregation Principle - focused on post operations.
 * Follows Dependency Inversion Principle - abstracts implementation details.
 */
public interface PostService {

    /**
     * Create a new post and execute appropriate fanout strategy.
     *
     * @param post the post to create
     * @return created post
     */
    Post createPost(Post post);

    /**
     * Get post by ID.
     *
     * @param postId the post ID
     * @return optional post
     */
    Optional<Post> getPostById(UUID postId);

    /**
     * Delete a post.
     *
     * @param postId the post ID
     */
    void deletePost(UUID postId);

    /**
     * Update post metrics (likes, retweets, replies).
     *
     * @param postId the post ID
     * @param likeCount new like count
     * @param retweetCount new retweet count
     * @param replyCount new reply count
     */
    void updatePostMetrics(UUID postId, Long likeCount, Long retweetCount, Long replyCount);
}
