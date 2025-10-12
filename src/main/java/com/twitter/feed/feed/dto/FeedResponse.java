package com.twitter.feed.feed.dto;

import com.twitter.feed.feed.model.FeedItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for feed retrieval.
 * Includes pagination metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedResponse {

    private List<FeedItem> items;
    private int totalItems;
    private int limit;
    private int offset;
    private boolean hasMore;
}
