-- PostgreSQL Initialization Script for Twitter Feed System
-- This script creates the necessary tables, indexes, and initial data

\c twitter_feed;

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Drop tables if they exist (for clean restart)
DROP TABLE IF EXISTS followers CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- ============================================================================
-- Users Table
-- ============================================================================
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(100),
    bio TEXT,
    follower_count BIGINT DEFAULT 0,
    following_count BIGINT DEFAULT 0,
    is_celebrity BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Followers Relationship Table
-- ============================================================================
CREATE TABLE followers (
    id BIGSERIAL PRIMARY KEY,
    follower_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(follower_id, following_id),
    CONSTRAINT check_not_self_follow CHECK (follower_id != following_id)
);

-- ============================================================================
-- Indexes for Performance
-- ============================================================================
CREATE INDEX idx_followers_follower_id ON followers(follower_id);
CREATE INDEX idx_followers_following_id ON followers(following_id);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_is_celebrity ON users(is_celebrity);
CREATE INDEX idx_users_created_at ON users(created_at);

-- ============================================================================
-- Function to Update Follower Counts
-- ============================================================================
CREATE OR REPLACE FUNCTION update_follower_counts()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        -- Increment follower count for the user being followed
        UPDATE users SET follower_count = follower_count + 1
        WHERE id = NEW.following_id;

        -- Increment following count for the follower
        UPDATE users SET following_count = following_count + 1
        WHERE id = NEW.follower_id;

        -- Check if following_id user should become a celebrity
        UPDATE users SET is_celebrity = TRUE
        WHERE id = NEW.following_id AND follower_count >= 10000 AND is_celebrity = FALSE;

        RETURN NEW;
    ELSIF (TG_OP = 'DELETE') THEN
        -- Decrement follower count for the user being unfollowed
        UPDATE users SET follower_count = GREATEST(follower_count - 1, 0)
        WHERE id = OLD.following_id;

        -- Decrement following count for the follower
        UPDATE users SET following_count = GREATEST(following_count - 1, 0)
        WHERE id = OLD.follower_id;

        -- Check if following_id user should lose celebrity status
        UPDATE users SET is_celebrity = FALSE
        WHERE id = OLD.following_id AND follower_count < 10000 AND is_celebrity = TRUE;

        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- Trigger to Automatically Update Follower Counts
-- ============================================================================
CREATE TRIGGER trigger_update_follower_counts
AFTER INSERT OR DELETE ON followers
FOR EACH ROW EXECUTE FUNCTION update_follower_counts();

-- ============================================================================
-- Function to Update updated_at Timestamp
-- ============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- Trigger to Update updated_at on users table
-- ============================================================================
CREATE TRIGGER trigger_update_users_timestamp
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- Insert Sample Data for Testing
-- ============================================================================

-- Insert regular users (< 10K followers)
INSERT INTO users (username, email, display_name, bio) VALUES
('john_doe', 'john@example.com', 'John Doe', 'Software Engineer'),
('jane_smith', 'jane@example.com', 'Jane Smith', 'Product Manager'),
('bob_wilson', 'bob@example.com', 'Bob Wilson', 'Designer'),
('alice_brown', 'alice@example.com', 'Alice Brown', 'Data Scientist'),
('charlie_davis', 'charlie@example.com', 'Charlie Davis', 'DevOps Engineer');

-- Insert celebrity users (simulate high follower count)
INSERT INTO users (username, email, display_name, bio, follower_count, is_celebrity) VALUES
('tech_influencer', 'tech@example.com', 'Tech Influencer', 'Tech tips and tutorials', 15000, TRUE),
('celebrity_chef', 'chef@example.com', 'Celebrity Chef', 'Cooking with passion', 50000, TRUE),
('fitness_guru', 'fitness@example.com', 'Fitness Guru', 'Health and wellness expert', 25000, TRUE);

-- Create some follower relationships
INSERT INTO followers (follower_id, following_id) VALUES
-- Regular users following each other
(1, 2), (1, 3), (1, 4),
(2, 1), (2, 3), (2, 5),
(3, 1), (3, 2), (3, 4), (3, 5),
(4, 1), (4, 2), (4, 3),
(5, 1), (5, 2), (5, 3), (5, 4),

-- Regular users following celebrities
(1, 6), (1, 7), (1, 8),
(2, 6), (2, 7), (2, 8),
(3, 6), (3, 7), (3, 8),
(4, 6), (4, 7),
(5, 6), (5, 8);

-- ============================================================================
-- Grant Permissions to twitter_admin user
-- ============================================================================
-- Grant all privileges on database and schema
GRANT ALL PRIVILEGES ON DATABASE twitter_feed TO twitter_admin;
GRANT ALL PRIVILEGES ON SCHEMA public TO twitter_admin;

-- Grant table privileges
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO twitter_admin;

-- Grant sequence privileges (for BIGSERIAL auto-increment)
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO twitter_admin;

-- Grant function/procedure privileges
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO twitter_admin;

-- Set default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT ALL PRIVILEGES ON TABLES TO twitter_admin;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT ALL PRIVILEGES ON SEQUENCES TO twitter_admin;

-- ============================================================================
-- Verification Queries
-- ============================================================================
\echo 'Database initialization completed!'
\echo 'Verifying setup...'
\echo ''
\echo 'Total users:'
SELECT COUNT(*) as total_users FROM users;
\echo ''
\echo 'Celebrity users:'
SELECT username, follower_count FROM users WHERE is_celebrity = TRUE;
\echo ''
\echo 'Total follower relationships:'
SELECT COUNT(*) as total_followers FROM followers;
\echo ''
\echo 'Database ready for use!'
