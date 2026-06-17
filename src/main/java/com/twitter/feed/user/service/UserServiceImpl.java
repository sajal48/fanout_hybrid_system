package com.twitter.feed.user.service;

import com.twitter.feed.common.exception.ResourceNotFoundException;
import com.twitter.feed.config.FeedConfig;
import com.twitter.feed.user.exception.DuplicateFollowerException;
import com.twitter.feed.user.model.Follower;
import com.twitter.feed.user.model.User;
import com.twitter.feed.user.repository.FollowerRepository;
import com.twitter.feed.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of UserService.
 * Handles user and follower relationship operations.
 *
 * Follows Single Responsibility Principle - manages user operations.
 * Follows Dependency Inversion Principle - depends on repository interfaces.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final FollowerRepository followerRepository;
    private final FeedConfig feedConfig;

    @Override
    @Cacheable(value = "users", key = "#userId", cacheManager = "localCacheManager")
    public Optional<User> getUserById(Long userId) {
        return userRepository.findById(userId);
    }

    @Override
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    @Transactional
    public User createUser(User user) {
        // Check for duplicate username/email
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new DuplicateFollowerException("Username already exists: " + user.getUsername());
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateFollowerException("Email already exists: " + user.getEmail());
        }

        User savedUser = userRepository.save(user);
        log.info("Created user: {} with ID: {}", user.getUsername(), savedUser.getId());
        return savedUser;
    }

    @Override
    @Transactional
    public User updateUser(User user) {
        if (!userRepository.existsById(user.getId())) {
            throw new ResourceNotFoundException("User", user.getId());
        }

        User updatedUser = userRepository.save(user);
        log.info("Updated user: {}", user.getId());
        return updatedUser;
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }

        userRepository.deleteById(userId);
        log.info("Deleted user: {}", userId);
    }

    @Override
    @Transactional
    public void followUser(Long followerId, Long followingId) {
        // Validate users exist
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", followerId));
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new ResourceNotFoundException("User", followingId));

        // Check if already following
        if (followerRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new DuplicateFollowerException(followerId, followingId);
        }

        // Create follower relationship
        Follower followerRelation = Follower.builder()
                .followerId(followerId)
                .followingId(followingId)
                .build();

        followerRepository.save(followerRelation);

        // Note: Follower counts are updated by database triggers
        // Check if following user should become celebrity
        long newFollowerCount = following.getFollowerCount() + 1;
        if (newFollowerCount >= feedConfig.getCelebrityThreshold() && !following.getIsCelebrity()) {
            updateCelebrityStatus(followingId, true);
        }

        log.info("User {} followed user {}", followerId, followingId);
    }

    @Override
    @Transactional
    public void unfollowUser(Long followerId, Long followingId) {
        // Validate relationship exists
        if (!followerRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new ResourceNotFoundException("Follower relationship not found");
        }

        followerRepository.deleteByFollowerIdAndFollowingId(followerId, followingId);

        // Check if following user should lose celebrity status
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new ResourceNotFoundException("User", followingId));

        long newFollowerCount = following.getFollowerCount() - 1;
        if (newFollowerCount < feedConfig.getCelebrityThreshold() && following.getIsCelebrity()) {
            updateCelebrityStatus(followingId, false);
        }

        log.info("User {} unfollowed user {}", followerId, followingId);
    }

    @Override
    public List<Long> getFollowerIds(Long userId) {
        return followerRepository.findFollowerIdsByFollowingId(userId);
    }

    @Override
    public List<Long> getFollowingIds(Long userId) {
        return followerRepository.findFollowingIdsByFollowerId(userId);
    }

    @Override
    public List<Long> getCelebrityFollowingIds(Long userId) {
        return followerRepository.findCelebrityFollowingIdsByFollowerId(userId);
    }

    @Override
    public boolean isFollowing(Long followerId, Long followingId) {
        return followerRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }

    @Override
    @Transactional
    public void updateCelebrityStatus(Long userId, boolean isCelebrity) {
        userRepository.updateCelebrityStatus(userId, isCelebrity);
        log.info("Updated celebrity status for user {} to {}", userId, isCelebrity);
    }
}
