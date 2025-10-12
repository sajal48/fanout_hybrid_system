package com.twitter.feed.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * Request DTO for creating a new post.
 * Follows DTO pattern - decouples API contract from domain model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Post content cannot be empty")
    @Size(max = 280, message = "Post content cannot exceed 280 characters")
    private String content;

    private List<String> mediaUrls;

    private Set<String> hashtags;
}
