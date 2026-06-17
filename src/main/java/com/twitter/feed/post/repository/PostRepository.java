package com.twitter.feed.post.repository;

import com.twitter.feed.post.model.Post;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for Post entity (Cassandra).
 * Follows Interface Segregation Principle - focused on post operations only.
 * Follows Dependency Inversion Principle - depends on abstraction.
 */
@Repository
public interface PostRepository extends CassandraRepository<Post, UUID> {

    /**
     * Find posts by user ID.
     *
     * @param userId the user ID
     * @return list of posts
     */
    List<Post> findByUserId(Long userId);

    /**
     * Find recent posts by user ID.
     *
     * @param userId the user ID
     * @param since timestamp to filter from
     * @return list of recent posts
     */
    @Query("SELECT * FROM posts WHERE user_id = ?0 AND created_at >= ?1 ALLOW FILTERING")
    List<Post> findByUserIdAndCreatedAtAfter(Long userId, Instant since);

    /**
     * Find posts containing a hashtag.
     *
     * @param hashtag the hashtag
     * @return list of posts
     */
    @Query("SELECT * FROM posts WHERE hashtags CONTAINS ?0 ALLOW FILTERING")
    List<Post> findByHashtagsContaining(String hashtag);

    /**
     * Find multiple posts by IDs (batch operation).
     * OPTIMIZATION: Reduces N queries to 1 batch query.
     *
     * @param postIds list of post IDs
     * @return list of posts with matching IDs
     */
    @Query("SELECT * FROM posts WHERE post_id IN ?0")
    List<Post> findByIds(List<UUID> postIds);
}
