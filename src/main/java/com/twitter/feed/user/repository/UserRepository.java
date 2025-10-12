package com.twitter.feed.user.repository;

import com.twitter.feed.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for User entity (PostgreSQL).
 * Follows Interface Segregation Principle - focused on user operations only.
 * Follows Dependency Inversion Principle - depends on abstraction.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username.
     *
     * @param username the username
     * @return optional user
     */
    Optional<User> findByUsername(String username);

    /**
     * Find user by email.
     *
     * @param email the email
     * @return optional user
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if username exists.
     *
     * @param username the username
     * @return true if exists
     */
    boolean existsByUsername(String username);

    /**
     * Check if email exists.
     *
     * @param email the email
     * @return true if exists
     */
    boolean existsByEmail(String email);

    /**
     * Update celebrity status for a user.
     *
     * @param userId the user ID
     * @param isCelebrity the celebrity status
     */
    @Modifying
    @Query("UPDATE User u SET u.isCelebrity = :isCelebrity WHERE u.id = :userId")
    void updateCelebrityStatus(@Param("userId") Long userId, @Param("isCelebrity") Boolean isCelebrity);

    /**
     * Increment follower count.
     *
     * @param userId the user ID
     */
    @Modifying
    @Query("UPDATE User u SET u.followerCount = u.followerCount + 1 WHERE u.id = :userId")
    void incrementFollowerCount(@Param("userId") Long userId);

    /**
     * Decrement follower count.
     *
     * @param userId the user ID
     */
    @Modifying
    @Query("UPDATE User u SET u.followerCount = u.followerCount - 1 WHERE u.id = :userId AND u.followerCount > 0")
    void decrementFollowerCount(@Param("userId") Long userId);
}
