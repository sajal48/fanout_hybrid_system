# Hybrid Fanout Twitter-like Feed System Design

## Overview

This document outlines the design of a hybrid fanout system for a Twitter-like social media feed mechanism. The system is designed to handle both users with small follower counts and celebrities/influencers with large follower counts efficiently.

## System Architecture

### High-Level Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Client Apps   │    │   Load Balancer  │    │  API Gateway    │
│  (Web/Mobile)   │◄──►│                  │◄──►│                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                                         │
                                                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Feed Service Cluster                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  Post Service   │  │  Feed Service   │  │  User Service   │ │
│  │                 │  │                 │  │                 │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Data Layer                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   PostgreSQL    │  │   Apache        │  │      Redis      │ │
│  │   (Metadata)    │  │   Cassandra     │  │   (Cache)       │ │
│  │                 │  │   (Posts &      │  │                 │ │
│  │                 │  │    Feeds)       │  │                 │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## Hybrid Fanout Strategy

### Phase 1: Dual Database Approach

The system employs a hybrid fanout strategy that optimizes for both small and large follower scenarios:

#### 1. Small Follower Users (< 10K followers)
- **Fan-out on Write**: Pre-compute feeds for all followers
- **Storage**: Individual user feed caches in Redis
- **Advantage**: Fast read performance
- **Trade-off**: Higher write overhead

#### 2. Large Follower Users (≥ 10K followers)
- **Fan-out on Read**: Compute feeds on demand
- **Storage**: Dedicated high-performance Cassandra partition
- **Advantage**: Reduced write overhead
- **Trade-off**: Higher read latency

### Feed Generation Flow

```
┌─────────────────┐
│   User Posts    │
│   New Tweet     │
└─────────┬───────┘
          │
          ▼
┌─────────────────┐
│ Check Follower  │
│     Count       │
└─────────┬───────┘
          │
     ┌────▼────┐
     │ < 10K?  │
     └────┬────┘
          │
    ┌─────▼─────┐        ┌─────────────────┐
    │    YES    │        │       NO        │
    │           │        │                 │
    ▼           │        ▼                 │
┌─────────────┐ │  ┌─────────────────┐    │
│ Fan-out on  │ │  │  Store in       │    │
│   Write     │ │  │  Celebrity DB   │    │
│             │ │  │                 │    │
│ Update all  │ │  │ Fan-out on Read │    │
│ follower    │ │  │                 │    │
│ feed caches │ │  └─────────────────┘    │
└─────────────┘ │                         │
          │     │                         │
          └─────┼─────────────────────────┘
                │
                ▼
        ┌─────────────────┐
        │  Feed Updated   │
        └─────────────────┘
```

## Database Design

### PostgreSQL Schema (Metadata & User Information)

```sql
-- Users table
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

-- Followers relationship
CREATE TABLE followers (
    id BIGSERIAL PRIMARY KEY,
    follower_id BIGINT REFERENCES users(id),
    following_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(follower_id, following_id)
);

-- Create indexes
CREATE INDEX idx_followers_follower_id ON followers(follower_id);
CREATE INDEX idx_followers_following_id ON followers(following_id);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_is_celebrity ON users(is_celebrity);
```

### Apache Cassandra Schema (Posts & Celebrity Feeds)

```cql
-- Keyspace creation
CREATE KEYSPACE IF NOT EXISTS twitter_feeds 
WITH REPLICATION = {
    'class': 'SimpleStrategy', 
    'replication_factor': 3
};

-- Posts table
CREATE TABLE IF NOT EXISTS twitter_feeds.posts (
    post_id UUID PRIMARY KEY,
    user_id BIGINT,
    content TEXT,
    media_urls LIST<TEXT>,
    hashtags SET<TEXT>,
    mentions SET<BIGINT>,
    like_count BIGINT DEFAULT 0,
    retweet_count BIGINT DEFAULT 0,
    reply_count BIGINT DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- User timeline (for celebrity users)
CREATE TABLE IF NOT EXISTS twitter_feeds.user_timeline (
    user_id BIGINT,
    post_id UUID,
    created_at TIMESTAMP,
    content TEXT,
    author_id BIGINT,
    author_username TEXT,
    PRIMARY KEY (user_id, created_at, post_id)
) WITH CLUSTERING ORDER BY (created_at DESC, post_id ASC);

-- Celebrity posts (for fan-out on read)
CREATE TABLE IF NOT EXISTS twitter_feeds.celebrity_posts (
    user_id BIGINT,
    post_id UUID,
    created_at TIMESTAMP,
    content TEXT,
    media_urls LIST<TEXT>,
    hashtags SET<TEXT>,
    PRIMARY KEY (user_id, created_at, post_id)
) WITH CLUSTERING ORDER BY (created_at DESC, post_id ASC);

-- Create indexes
CREATE INDEX IF NOT EXISTS ON twitter_feeds.posts (user_id);
CREATE INDEX IF NOT EXISTS ON twitter_feeds.posts (created_at);
```

### Redis Cache Structure

```
# User feed cache (for small follower users)
Key: "user_feed:{user_id}"
Type: Sorted Set
Score: timestamp
Value: post_id

# User metadata cache
Key: "user:{user_id}"
Type: Hash
Fields: username, display_name, follower_count, is_celebrity

# Post cache
Key: "post:{post_id}"
Type: Hash
Fields: user_id, content, created_at, like_count, etc.

# Celebrity users list
Key: "celebrity_users"
Type: Set
Value: user_ids of celebrity users

# Follower count cache
Key: "follower_count:{user_id}"
Type: String
Value: follower count
```

## API Design

### Core Endpoints

#### 1. Post Creation
```http
POST /api/v1/posts
Content-Type: application/json

{
    "content": "This is a sample tweet",
    "media_urls": ["https://example.com/image.jpg"],
    "hashtags": ["#example", "#twitter"]
}

Response:
{
    "post_id": "123e4567-e89b-12d3-a456-426614174000",
    "status": "success",
    "fanout_strategy": "write|read",
    "affected_users": 1500
}
```

#### 2. Feed Retrieval
```http
GET /api/v1/feed?limit=20&cursor=cursor_token

Response:
{
    "posts": [
        {
            "post_id": "123e4567-e89b-12d3-a456-426614174000",
            "user": {
                "id": 12345,
                "username": "john_doe",
                "display_name": "John Doe"
            },
            "content": "This is a sample tweet",
            "created_at": "2025-10-12T10:30:00Z",
            "like_count": 25,
            "retweet_count": 5,
            "reply_count": 3
        }
    ],
    "next_cursor": "next_cursor_token",
    "has_more": true
}
```

## Technology Stack

### Backend Framework
- **Spring Boot 3.x** with Java 17
- **Maven** for dependency management
- **Spring Data JPA** for PostgreSQL
- **Spring Data Cassandra** for Cassandra
- **Spring Data Redis** for Redis

### Databases
- **PostgreSQL 15+**: User metadata, relationships
- **Apache Cassandra 4.x**: Posts, celebrity feeds
- **Redis 7+**: Caching, user feeds

### Additional Dependencies
```xml
<dependencies>
    <!-- Spring Boot Starters -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-cassandra</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    
    <!-- Database Drivers -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
    
    <!-- Utilities -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

## Scalability Considerations

### Phase 1 Implementation
1. **Threshold-based fanout**: 10K followers as the threshold
2. **Database partitioning**: Separate read/write paths
3. **Caching strategy**: Redis for hot data
4. **Async processing**: Message queues for feed updates

### Future Enhancements (Regional Sharing)
1. **Multi-region deployment**: CDN integration
2. **Read replicas**: Regional PostgreSQL and Cassandra replicas
3. **Cache sharding**: Redis clustering across regions
4. **Event sourcing**: For audit and replay capabilities

## Performance Metrics

### Target Performance
- **Feed generation**: < 100ms for 95th percentile
- **Post creation**: < 200ms for write fanout
- **Celebrity post**: < 50ms (no immediate fanout)
- **Feed retrieval**: < 150ms for 95th percentile

### Monitoring
- Database connection pools
- Cache hit ratios
- API response times
- Queue processing delays

## Security Considerations

1. **Authentication**: JWT-based authentication
2. **Rate limiting**: Per-user and per-IP limits
3. **Input validation**: Content sanitization
4. **Data encryption**: At rest and in transit
5. **Privacy controls**: User visibility settings

## Deployment Architecture

### Container Strategy
- **Docker**: Containerized services
- **Kubernetes**: Orchestration platform
- **Service mesh**: Istio for communication

### Infrastructure
- **Load balancers**: Application-level load balancing
- **Auto-scaling**: Horizontal pod autoscaling
- **Health checks**: Liveness and readiness probes
- **Logging**: Centralized logging with ELK stack

## Implementation Phases

### Phase 1: Core Functionality ✅
- [x] Hybrid fanout mechanism
- [x] Basic CRUD operations
- [x] Database schema design
- [x] Caching strategy

### Phase 2: Performance Optimization
- [ ] Connection pooling optimization
- [ ] Query optimization
- [ ] Cache warming strategies
- [ ] Batch processing

### Phase 3: Advanced Features
- [ ] Real-time notifications
- [ ] Analytics and metrics
- [ ] Content recommendation
- [ ] Regional deployment

### Phase 4: Scale & Reliability
- [ ] Multi-region support
- [ ] Disaster recovery
- [ ] Advanced monitoring
- [ ] Capacity planning

This system design provides a robust foundation for a Twitter-like feed system that can handle both small and large-scale user scenarios efficiently while maintaining performance and scalability.