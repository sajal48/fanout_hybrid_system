package com.twitter.feed.post.controller;

import com.twitter.feed.common.dto.ApiResponse;
import com.twitter.feed.common.exception.ResourceNotFoundException;
import com.twitter.feed.post.dto.CreatePostRequest;
import com.twitter.feed.post.dto.PostResponse;
import com.twitter.feed.post.model.Post;
import com.twitter.feed.post.service.AsyncPostService;
import com.twitter.feed.post.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for post operations.
 *
 * Endpoints:
 * - POST /api/v1/posts - Create new post (async, returns 202 ACCEPTED)
 * - GET /api/v1/posts/{postId} - Get post by ID
 * - DELETE /api/v1/posts/{postId} - Delete post
 *
 * POST endpoint now returns immediately (202 ACCEPTED) with post queued
 * for background processing. Fanout and writes happen asynchronously.
 *
 * Follows Single Responsibility Principle - handles HTTP layer only.
 * Follows Dependency Inversion Principle - depends on service interfaces.
 */
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;
    private final AsyncPostService asyncPostService;

    /**
     * Create a new post asynchronously.
     *
     * Returns immediately (202 ACCEPTED) with post queued for processing.
     * Actual creation happens in background with batching optimization.
     *
     * @param request the post creation request
     * @return provisional post response
     * @throws IllegalStateException if queue is full
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @Valid @RequestBody CreatePostRequest request) {

        log.info("Queuing post creation for user {}", request.getUserId());

        // Build post entity
        Post post = Post.builder()
                .userId(request.getUserId())
                .content(request.getContent())
                .mediaUrls(request.getMediaUrls())
                .hashtags(request.getHashtags())
                .build();

        try {
            // Queue for async processing - returns immediately
            AsyncPostService.PostCreationTask task = asyncPostService.queuePostCreation(post);

            // Build response with provisional post ID
            PostResponse response = convertToResponse(task.getPost());

            log.info("Post {} queued for creation (queue size: {})",
                    task.getPost().getPost_id(),
                    asyncPostService.getQueueSize());

            // Return 202 ACCEPTED - processing in background
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success("Post creation queued for processing", response));

        } catch (IllegalStateException e) {
            log.error("Failed to queue post: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("System is under heavy load. Please try again later."));
        }
    }

    /**
     * Get post by ID.
     *
     * @param postId the post ID
     * @return post data
     */
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> getPostById(
            @PathVariable UUID postId) {

        log.debug("Retrieving post: {}", postId);

        Post post = postService.getPostById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId.toString()));

        PostResponse response = convertToResponse(post);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Delete post by ID.
     *
     * @param postId the post ID
     * @return success response
     */
    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable UUID postId) {

        log.info("Deleting post: {}", postId);

        postService.deletePost(postId);

        return ResponseEntity.ok(ApiResponse.success("Post deleted successfully", null));
    }

    /**
     * Update post metrics (likes, retweets, replies).
     *
     * @param postId the post ID
     * @param likeCount new like count
     * @param retweetCount new retweet count
     * @param replyCount new reply count
     * @return success response
     */
    @PatchMapping("/{postId}/metrics")
    public ResponseEntity<ApiResponse<Void>> updateMetrics(
            @PathVariable UUID postId,
            @RequestParam(required = false, defaultValue = "0") Long likeCount,
            @RequestParam(required = false, defaultValue = "0") Long retweetCount,
            @RequestParam(required = false, defaultValue = "0") Long replyCount) {

        log.debug("Updating metrics for post {}", postId);

        postService.updatePostMetrics(postId, likeCount, retweetCount, replyCount);

        return ResponseEntity.ok(ApiResponse.success("Metrics updated successfully", null));
    }

    /**
     * Convert Post entity to PostResponse DTO.
     */
    private PostResponse convertToResponse(Post post) {
        return PostResponse.builder()
                .postId(post.getPost_id())
                .userId(post.getUserId())
                .username(post.getUsername())
                .content(post.getContent())
                .mediaUrls(post.getMediaUrls())
                .hashtags(post.getHashtags())
                .likeCount(post.getLikeCount())
                .retweetCount(post.getRetweetCount())
                .replyCount(post.getReplyCount())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
