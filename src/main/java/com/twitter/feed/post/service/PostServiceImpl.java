package com.twitter.feed.post.service;

import com.twitter.feed.common.exception.ResourceNotFoundException;
import com.twitter.feed.fanout.strategy.FanoutStrategy;
import com.twitter.feed.fanout.strategy.FanoutStrategyFactory;
import com.twitter.feed.post.exception.InvalidPostException;
import com.twitter.feed.post.model.Post;
import com.twitter.feed.post.repository.PostRepository;
import com.twitter.feed.user.model.User;
import com.twitter.feed.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of PostService.
 * Orchestrates post creation and fanout strategy execution.
 *
 * Follows Single Responsibility Principle - manages post operations.
 * Follows Open/Closed Principle - uses strategy pattern for fanout.
 * Follows Dependency Inversion Principle - depends on abstractions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final UserService userService;
    private final FanoutStrategyFactory fanoutStrategyFactory;

    @Override
    @Transactional
    public Post createPost(Post post) {
        // Validate input
        validatePost(post);

        // Get user and verify exists
        User user = userService.getUserById(post.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", post.getUserId()));

        // Set timestamps
        if (post.getCreatedAt() == null) {
            post.setCreatedAt(Instant.now());
        }
        if (post.getUpdatedAt() == null) {
            post.setUpdatedAt(Instant.now());
        }

        // Set username
        post.setUsername(user.getUsername());

        // Save post to Cassandra
        Post savedPost = postRepository.save(post);
        log.info("Created post {} for user {}", savedPost.getPostId(), user.getId());

        // Execute fanout strategy based on follower count
        try {
            FanoutStrategy strategy = fanoutStrategyFactory.getStrategy(user.getFollowerCount());
            List<Long> followerIds = userService.getFollowerIds(user.getId());

            log.info("Executing {} for post {} with {} followers",
                    strategy.getStrategyName(), savedPost.getPostId(), followerIds.size());

            strategy.executeFanout(savedPost, followerIds);

        } catch (Exception e) {
            log.error("Fanout failed for post {}, but post was saved", savedPost.getPostId(), e);
            // Post is saved even if fanout fails
        }

        return savedPost;
    }

    @Override
    public Optional<Post> getPostById(UUID postId) {
        return postRepository.findById(postId);
    }

    @Override
    @Transactional
    public void deletePost(UUID postId) {
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post", "id", postId.toString());
        }

        postRepository.deleteById(postId);
        log.info("Deleted post: {}", postId);
    }

    @Override
    @Transactional
    public void updatePostMetrics(UUID postId, Long likeCount, Long retweetCount, Long replyCount) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId.toString()));

        post.setLikeCount(likeCount);
        post.setRetweetCount(retweetCount);
        post.setReplyCount(replyCount);
        post.setUpdatedAt(Instant.now());

        postRepository.save(post);
        log.debug("Updated metrics for post {}", postId);
    }

    /**
     * Validate post data.
     * Follows fail-fast principle.
     */
    private void validatePost(Post post) {
        if (post.getUserId() == null) {
            throw new InvalidPostException("User ID is required");
        }

        if (post.getContent() == null || post.getContent().trim().isEmpty()) {
            throw new InvalidPostException("Post content cannot be empty");
        }

        if (post.getContent().length() > 280) {
            throw new InvalidPostException("Post content cannot exceed 280 characters");
        }
    }
}
