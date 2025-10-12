package com.twitter.feed.post.controller;

import com.twitter.feed.common.dto.ApiResponse;
import com.twitter.feed.common.exception.ResourceNotFoundException;
import com.twitter.feed.post.dto.CreatePostRequest;
import com.twitter.feed.post.dto.PostResponse;
import com.twitter.feed.post.model.Post;
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
 * - POST /api/v1/posts - Create new post
 * - GET /api/v1/posts/{postId} - Get post by ID
 * - DELETE /api/v1/posts/{postId} - Delete post
 *
 * Follows Single Responsibility Principle - handles HTTP layer only.
 * Follows Dependency Inversion Principle - depends on PostService interface.
 */
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;

    /**
     * Create a new post.
     *
     * @param request the post creation request
     * @return created post with fanout strategy information
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @Valid @RequestBody CreatePostRequest request) {

        log.info("Creating post for user {}", request.getUserId());

        // Convert DTO to domain model
        Post post = Post.builder()
                .userId(request.getUserId())
                .content(request.getContent())
                .mediaUrls(request.getMediaUrls())
                .hashtags(request.getHashtags())
                .build();

        // Create post (async fanout happens in background)
        Post createdPost = postService.createPost(post);

        // Convert to response DTO
        PostResponse response = convertToResponse(createdPost);

        log.info("Post created: {} for user {}", createdPost.getPostId(), request.getUserId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Post created successfully", response));
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
                .postId(post.getPostId())
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
