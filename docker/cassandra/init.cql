-- Cassandra Initialization Script for Twitter Feed System
-- This script creates the keyspace, tables, and indexes

-- ============================================================================
-- Create Keyspace
-- ============================================================================
CREATE KEYSPACE IF NOT EXISTS twitter_feeds
WITH REPLICATION = {
    'class': 'SimpleStrategy',
    'replication_factor': 1
};

-- Use the keyspace
USE twitter_feeds;

-- ============================================================================
-- Posts Table - Stores all posts
-- ============================================================================
CREATE TABLE IF NOT EXISTS posts (
    post_id UUID PRIMARY KEY,
    user_id BIGINT,
    username TEXT,
    content TEXT,
    media_urls LIST<TEXT>,
    hashtags SET<TEXT>,
    mentions SET<BIGINT>,
    like_count BIGINT,
    retweet_count BIGINT,
    reply_count BIGINT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- ============================================================================
-- User Timeline Table - For celebrity users (fan-out on read)
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_timeline (
    user_id BIGINT,
    created_at TIMESTAMP,
    post_id UUID,
    content TEXT,
    author_id BIGINT,
    author_username TEXT,
    media_urls LIST<TEXT>,
    hashtags SET<TEXT>,
    like_count BIGINT,
    retweet_count BIGINT,
    reply_count BIGINT,
    PRIMARY KEY (user_id, created_at, post_id)
) WITH CLUSTERING ORDER BY (created_at DESC, post_id ASC)
  AND gc_grace_seconds = 86400
  AND compaction = {
    'class': 'TimeWindowCompactionStrategy',
    'compaction_window_size': 1,
    'compaction_window_unit': 'DAYS'
  };

-- ============================================================================
-- Celebrity Posts Table - Optimized for fan-out on read
-- ============================================================================
CREATE TABLE IF NOT EXISTS celebrity_posts (
    user_id BIGINT,
    created_at TIMESTAMP,
    post_id UUID,
    content TEXT,
    username TEXT,
    media_urls LIST<TEXT>,
    hashtags SET<TEXT>,
    mentions SET<BIGINT>,
    like_count BIGINT,
    retweet_count BIGINT,
    reply_count BIGINT,
    PRIMARY KEY (user_id, created_at, post_id)
) WITH CLUSTERING ORDER BY (created_at DESC, post_id ASC)
  AND gc_grace_seconds = 86400
  AND compaction = {
    'class': 'TimeWindowCompactionStrategy',
    'compaction_window_size': 1,
    'compaction_window_unit': 'DAYS'
  };

-- ============================================================================
-- Feed Cache Table - Pre-computed feeds for regular users
-- ============================================================================
CREATE TABLE IF NOT EXISTS feed_cache (
    user_id BIGINT,
    created_at TIMESTAMP,
    post_id UUID,
    author_id BIGINT,
    author_username TEXT,
    content TEXT,
    media_urls LIST<TEXT>,
    hashtags SET<TEXT>,
    like_count BIGINT,
    retweet_count BIGINT,
    reply_count BIGINT,
    PRIMARY KEY (user_id, created_at, post_id)
) WITH CLUSTERING ORDER BY (created_at DESC, post_id ASC)
  AND gc_grace_seconds = 86400
  AND default_time_to_live = 604800;  -- 7 days TTL

-- ============================================================================
-- Post Metrics Table - Engagement metrics for posts
-- ============================================================================
CREATE TABLE IF NOT EXISTS post_metrics (
    post_id UUID PRIMARY KEY,
    user_id BIGINT,
    view_count BIGINT,
    like_count BIGINT,
    retweet_count BIGINT,
    reply_count BIGINT,
    share_count BIGINT,
    last_updated TIMESTAMP
);

-- ============================================================================
-- Hashtag Posts Table - For hashtag-based queries
-- ============================================================================
CREATE TABLE IF NOT EXISTS hashtag_posts (
    hashtag TEXT,
    created_at TIMESTAMP,
    post_id UUID,
    user_id BIGINT,
    username TEXT,
    content TEXT,
    PRIMARY KEY (hashtag, created_at, post_id)
) WITH CLUSTERING ORDER BY (created_at DESC, post_id ASC)
  AND gc_grace_seconds = 86400;

-- ============================================================================
-- User Mentions Table - For mention-based queries
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_mentions (
    mentioned_user_id BIGINT,
    created_at TIMESTAMP,
    post_id UUID,
    author_id BIGINT,
    author_username TEXT,
    content TEXT,
    PRIMARY KEY (mentioned_user_id, created_at, post_id)
) WITH CLUSTERING ORDER BY (created_at DESC, post_id ASC)
  AND gc_grace_seconds = 86400;

-- ============================================================================
-- Create Secondary Indexes
-- ============================================================================
CREATE INDEX IF NOT EXISTS ON posts (user_id);
CREATE INDEX IF NOT EXISTS ON posts (created_at);

-- ============================================================================
-- Insert Sample Data for Testing
-- ============================================================================

-- Sample posts from regular users
INSERT INTO posts (post_id, user_id, username, content, hashtags, like_count, retweet_count, reply_count, created_at, updated_at)
VALUES (
    uuid(),
    1,
    'john_doe',
    'Just deployed my first microservice! Feeling accomplished.',
    {'#coding', '#microservices'},
    15,
    3,
    2,
    toTimestamp(now()),
    toTimestamp(now())
);

INSERT INTO posts (post_id, user_id, username, content, hashtags, like_count, retweet_count, reply_count, created_at, updated_at)
VALUES (
    uuid(),
    2,
    'jane_smith',
    'Product roadmap planning for Q1. Exciting times ahead!',
    {'#productmanagement', '#planning'},
    25,
    5,
    4,
    toTimestamp(now()),
    toTimestamp(now())
);

-- Sample posts from celebrity users
INSERT INTO celebrity_posts (user_id, created_at, post_id, content, username, hashtags, like_count, retweet_count, reply_count)
VALUES (
    6,
    toTimestamp(now()),
    uuid(),
    'New tutorial series on distributed systems coming next week!',
    'tech_influencer',
    {'#tutorial', '#distributedsystems'},
    1500,
    300,
    75
);

INSERT INTO celebrity_posts (user_id, created_at, post_id, content, username, hashtags, like_count, retweet_count, reply_count)
VALUES (
    7,
    toTimestamp(now()),
    uuid(),
    'Just released my new cookbook! Check it out!',
    'celebrity_chef',
    {'#cooking', '#cookbook'},
    5000,
    1000,
    250
);

INSERT INTO celebrity_posts (user_id, created_at, post_id, content, username, hashtags, like_count, retweet_count, reply_count)
VALUES (
    8,
    toTimestamp(now()),
    uuid(),
    '30-day fitness challenge starts tomorrow! Who is in?',
    'fitness_guru',
    {'#fitness', '#challenge'},
    3500,
    800,
    180
);

-- ============================================================================
-- Verification
-- ============================================================================

-- Describe the keyspace
DESCRIBE KEYSPACE twitter_feeds;
