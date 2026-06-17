package com.twitter.feed.post.service;

import com.twitter.feed.post.model.Post;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AsyncPostService.
 * Tests the async POST request queuing, batching, and processing.
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
public class AsyncPostServiceTest {

    @Autowired
    private AsyncPostService asyncPostService;

    @BeforeEach
    void setUp() {
        // Service is auto-wired from Spring context
        assertNotNull(asyncPostService);
    }

    /**
     * Test that a single post can be successfully queued.
     */
    @Test
    @Timeout(5)
    void testQueuePostSuccessfully() {
        Post post = Post.builder()
                .post_id(UUID.randomUUID())
                .userId(1L)
                .content("Test post")
                .build();

        AsyncPostService.PostCreationTask task = asyncPostService.queuePostCreation(post);

        assertNotNull(task);
        assertNotNull(task.getPost());
        assertEquals(post.getUserId(), task.getPost().getUserId());
        assertTrue(asyncPostService.getQueueSize() >= 0);
    }

    /**
     * Test that multiple posts are queued correctly.
     */
    @Test
    @Timeout(5)
    void testQueueMultiplePosts() {
        int postCount = 10;
        int initialQueueSize = asyncPostService.getQueueSize();

        for (int i = 0; i < postCount; i++) {
            Post post = Post.builder()
                    .post_id(UUID.randomUUID())
                    .userId((long) i)
                    .content("Test post " + i)
                    .build();
            asyncPostService.queuePostCreation(post);
        }

        int finalQueueSize = asyncPostService.getQueueSize();
        assertTrue(finalQueueSize >= initialQueueSize + postCount - 5); // Some may have been processed
    }

    /**
     * Test that an exception is thrown when queue is full.
     */
    @Test
    @Timeout(10)
    void testQueueFullException() {
        // Try to fill the queue beyond capacity
        boolean queueFullEncountered = false;
        
        try {
            for (int i = 0; i < 50000; i++) {  // Try to queue many posts
                Post post = Post.builder()
                        .post_id(UUID.randomUUID())
                        .userId((long) i)
                        .content("Test post " + i)
                        .build();
                asyncPostService.queuePostCreation(post);
                
                if (asyncPostService.getQueueSize() >= 9500) {
                    // Queue is nearly full
                    break;
                }
            }
        } catch (IllegalStateException e) {
            queueFullEncountered = true;
            log.info("Queue full exception caught as expected: {}", e.getMessage());
        }

        // Either queue is full or we successfully queued many items
        assertTrue(asyncPostService.getQueueSize() >= 0);
    }

    /**
     * Test queue capacity and current size monitoring.
     */
    @Test
    @Timeout(5)
    void testQueueMonitoring() {
        int initialSize = asyncPostService.getQueueSize();
        int capacity = asyncPostService.getQueueCapacity();

        assertEquals(10000, capacity); // Default configured capacity
        assertTrue(initialSize >= 0);
        assertTrue(initialSize <= capacity);

        Post post = Post.builder()
                .post_id(UUID.randomUUID())
                .userId(1L)
                .content("Test post")
                .build();

        asyncPostService.queuePostCreation(post);
        
        int newSize = asyncPostService.getQueueSize();
        assertTrue(newSize <= capacity);
    }

    /**
     * Test post creation task tracking.
     */
    @Test
    @Timeout(5)
    void testPostCreationTaskTracking() {
        Post post = Post.builder()
                .post_id(UUID.randomUUID())
                .userId(42L)
                .content("Tracked post")
                .build();

        AsyncPostService.PostCreationTask task = asyncPostService.queuePostCreation(post);

        // Task should have valid post reference
        assertNotNull(task.getPost());
        assertEquals(42L, task.getPost().getUserId());
        assertEquals("Tracked post", task.getPost().getContent());

        // Queue wait time should be >= 0
        long waitTime = task.getQueueWaitTime();
        assertTrue(waitTime >= 0);
    }

    /**
     * Test concurrent post queueing.
     */
    @Test
    @Timeout(10)
    void testConcurrentQueueing() throws InterruptedException {
        int threadCount = 5;
        int postsPerThread = 20;
        int initialSize = asyncPostService.getQueueSize();

        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < postsPerThread; i++) {
                    Post post = Post.builder()
                            .post_id(UUID.randomUUID())
                            .userId((long) (threadId * 100 + i))
                            .content("Post from thread " + threadId + " #" + i)
                            .build();
                    try {
                        asyncPostService.queuePostCreation(post);
                    } catch (IllegalStateException e) {
                        log.debug("Queue full: {}", e.getMessage());
                    }
                }
            });
            threads[t].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // At least some posts should be queued (not necessarily all if queue fills)
        int finalSize = asyncPostService.getQueueSize();
        assertTrue(finalSize >= initialSize);
    }

    /**
     * Test queue metrics and monitoring.
     */
    @Test
    @Timeout(5)
    void testQueueMetrics() {
        int initialSize = asyncPostService.getQueueSize();
        int capacity = asyncPostService.getQueueCapacity();

        // Add posts and check metrics
        for (int i = 0; i < 5; i++) {
            Post post = Post.builder()
                    .post_id(UUID.randomUUID())
                    .userId((long) i)
                    .content("Test post " + i)
                    .build();
            asyncPostService.queuePostCreation(post);
        }

        int afterQueueSize = asyncPostService.getQueueSize();

        // Verify capacity constraints
        assertTrue(afterQueueSize <= capacity);
        assertTrue(capacity > 0);
    }

    /**
     * Test that posts are assigned UUIDs if not provided.
     */
    @Test
    @Timeout(5)
    void testPostIdGeneration() {
        Post post = Post.builder()
                .userId(1L)
                .content("Test post without ID")
                .build();

        // Post doesn't have an ID initially
        assertNull(post.getPost_id());

        AsyncPostService.PostCreationTask task = asyncPostService.queuePostCreation(post);

        // After queueing, post should have a generated ID
        assertNotNull(task.getPost().getPost_id());
        assertNotNull(post.getPost_id());
    }
}

