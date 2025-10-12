package com.twitter.feed.user.repository;

import com.twitter.feed.user.model.Follower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Follower entity (PostgreSQL).
 * Follows Interface Segregation Principle - focused on follower operations only.
 * Follows Dependency Inversion Principle - depends on abstraction.
 */
@Repository
public interface FollowerRepository extends JpaRepository<Follower, Long> {

    /**
     * Find follower relationship.
     *
     * @param followerId the follower user ID
     * @param followingId the following user ID
     * @return optional follower
     */
    Optional<Follower> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    /**
     * Check if follower relationship exists.
     *
     * @param followerId the follower user ID
     * @param followingId the following user ID
     * @return true if exists
     */
    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    /**
     * Get all follower IDs for a user.
     *
     * @param followingId the user being followed
     * @return list of follower IDs
     */
    @Query("SELECT f.followerId FROM Follower f WHERE f.followingId = :followingId")
    List<Long> findFollowerIdsByFollowingId(@Param("followingId") Long followingId);

    /**
     * Get all following IDs for a user.
     *
     * @param followerId the follower user ID
     * @return list of following IDs
     */
    @Query("SELECT f.followingId FROM Follower f WHERE f.followerId = :followerId")
    List<Long> findFollowingIdsByFollowerId(@Param("followerId") Long followerId);

    /**
     * Get celebrity users that a user follows.
     *
     * @param followerId the follower user ID
     * @return list of celebrity user IDs
     */
    @Query("SELECT f.followingId FROM Follower f JOIN User u ON f.followingId = u.id " +
           "WHERE f.followerId = :followerId AND u.isCelebrity = true")
    List<Long> findCelebrityFollowingIdsByFollowerId(@Param("followerId") Long followerId);

    /**
     * Count followers for a user.
     *
     * @param followingId the user being followed
     * @return follower count
     */
    long countByFollowingId(Long followingId);

    /**
     * Count following for a user.
     *
     * @param followerId the follower user ID
     * @return following count
     */
    long countByFollowerId(Long followerId);

    /**
     * Delete follower relationship.
     *
     * @param followerId the follower user ID
     * @param followingId the following user ID
     */
    void deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);
}
