package com.twitter.feed.post.repository;

import com.twitter.feed.post.model.CelebrityPost;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for CelebrityPost entity (Cassandra).
 * Optimized for fan-out on read strategy with user_id partition key.
 *
 * Follows Interface Segregation Principle - focused on celebrity post operations only.
 * Follows Dependency Inversion Principle - depends on abstraction.
 */
@Repository
public interface CelebrityPostRepository extends CassandraRepository<CelebrityPost, UUID> {

    /**
     * Find recent posts by celebrity user ID.
     * Uses partition key for efficient query.
     *
     * @param userId the celebrity user ID
     * @param limit maximum number of posts
     * @return list of recent celebrity posts
     */
    @Query("SELECT * FROM celebrity_posts WHERE user_id = ?0 LIMIT ?1")
    List<CelebrityPost> findRecentByUserId(Long userId, int limit);

    /**
     * Find posts by celebrity user ID after a certain timestamp.
     * Uses partition key and clustering key for efficient range query.
     *
     * @param userId the celebrity user ID
     * @param since timestamp to filter from
     * @param limit maximum number of posts
     * @return list of celebrity posts
     */
    @Query("SELECT * FROM celebrity_posts WHERE user_id = ?0 AND created_at >= ?1 LIMIT ?2")
    List<CelebrityPost> findByUserIdAndCreatedAtAfter(Long userId, Instant since, int limit);

    /**
     * Find posts by celebrity user ID between timestamps.
     * Useful for pagination with time-based cursors.
     *
     * @param userId the celebrity user ID
     * @param start start timestamp
     * @param end end timestamp
     * @param limit maximum number of posts
     * @return list of celebrity posts
     */
    @Query("SELECT * FROM celebrity_posts WHERE user_id = ?0 AND created_at >= ?1 AND created_at <= ?2 LIMIT ?3")
    List<CelebrityPost> findByUserIdAndCreatedAtBetween(Long userId, Instant start, Instant end, int limit);
}
