package com.twitter.feed.feed.service;

import com.twitter.feed.feed.model.FeedItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper service to merge and sort feed items from multiple sources.
 * Merges pre-computed feeds (Redis) with celebrity posts (Cassandra).
 *
 * Follows Single Responsibility Principle - only handles feed merging.
 */
@Component
@Slf4j
public class FeedMerger {

    /**
     * Merge feed items from different sources and sort by timestamp (newest first).
     *
     * @param cachedFeedItems items from Redis cache
     * @param celebrityFeedItems items from celebrity posts
     * @param limit maximum number of items to return
     * @return merged and sorted feed items
     */
    public List<FeedItem> mergeFeed(List<FeedItem> cachedFeedItems,
                                     List<FeedItem> celebrityFeedItems,
                                     int limit) {

        log.debug("Merging {} cached items with {} celebrity items",
                cachedFeedItems != null ? cachedFeedItems.size() : 0,
                celebrityFeedItems != null ? celebrityFeedItems.size() : 0);

        // Combine both sources
        Stream<FeedItem> combinedStream = Stream.concat(
                cachedFeedItems != null ? cachedFeedItems.stream() : Stream.empty(),
                celebrityFeedItems != null ? celebrityFeedItems.stream() : Stream.empty()
        );

        // Sort by createdAt DESC (newest first) and limit
        List<FeedItem> mergedFeed = combinedStream
                .sorted(Comparator.comparing(FeedItem::getCreatedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        log.debug("Merged feed contains {} items", mergedFeed.size());
        return mergedFeed;
    }

    /**
     * Merge multiple feed item lists.
     *
     * @param feedLists list of feed item lists
     * @param limit maximum number of items to return
     * @return merged and sorted feed items
     */
    public List<FeedItem> mergeMultiple(List<List<FeedItem>> feedLists, int limit) {
        Stream<FeedItem> combinedStream = feedLists.stream()
                .flatMap(List::stream);

        return combinedStream
                .sorted(Comparator.comparing(FeedItem::getCreatedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}
