# Hybrid Fanout Twitter Feed System

A scalable Twitter-like feed system that uses a hybrid fanout strategy to efficiently handle both regular users and celebrity accounts with millions of followers.

## Overview

This system implements two distinct feed generation strategies:

- **Fan-out on Write** (< 10K followers): Pre-computes feeds for fast reads
- **Fan-out on Read** (≥ 10K followers): Computes feeds on-demand to reduce write overhead

## Technology Stack

- **Backend**: Spring Boot 3.x with Java 17
- **Databases**:
  - PostgreSQL 15+ (user metadata, relationships)
  - Apache Cassandra 4.x (posts, timelines)
  - Redis 7+ (caching, feed storage)
- **Containerization**: Docker & Docker Compose

## Quick Start

### Prerequisites

- Docker Desktop (Windows/Mac) or Docker Engine + Docker Compose (Linux)
- 4GB RAM minimum
- 10GB free disk space

### Start the System

```bash
chmod +x deploy.sh
./deploy.sh start
```

This will:
1. Start PostgreSQL, Cassandra, and Redis containers
2. Initialize database schemas
3. Load sample data

### Verify Services

```bash
./deploy.sh status
```

### Access Databases

**PostgreSQL**:
```bash
./deploy.sh postgres
```

**Cassandra**:
```bash
./deploy.sh cassandra
```

**Redis**:
```bash
./deploy.sh redis
```

## Database Connection Details

### PostgreSQL
- **Host**: localhost:5432
- **Database**: twitter_feed
- **Username**: twitter_admin
- **Password**: twitter_password_123

### Cassandra
- **Host**: localhost:9042
- **Keyspace**: twitter_feeds

### Redis
- **Host**: localhost:6379

## Project Structure

```
hybrid_data_system/
├── docker/                      # Database configurations
│   ├── postgres/
│   │   └── init.sql            # PostgreSQL schema & sample data
│   ├── cassandra/
│   │   └── init.cql            # Cassandra schema & sample data
│   └── redis/
│       └── redis.conf          # Redis configuration
├── docker-compose.yml          # Docker services definition
├── deploy.sh                   # Deployment script (Linux/Mac)
├── deploy.bat                  # Deployment script (Windows)
├── SYSTEM_DESIGN.md           # Detailed architecture documentation
└── CLAUDE.md                  # Development guidelines
```

## Architecture Highlights

### Hybrid Fanout Strategy

**Small Users (< 10K followers)**:
- Posts are pushed to all followers' feed caches (Redis)
- Optimized for fast read performance
- Higher write overhead acceptable

**Celebrity Users (≥ 10K followers)**:
- Posts stored in celebrity-specific tables (Cassandra)
- Feeds computed on-demand during read
- Reduces write amplification

### Database Design

**PostgreSQL**:
- User profiles and metadata
- Follower/following relationships
- Automatic celebrity status management via triggers

**Cassandra**:
- Post storage with time-series optimization
- Celebrity posts partitioned by user_id
- User timelines with efficient time-based queries

**Redis**:
- Pre-computed feeds (sorted sets by timestamp)
- User metadata caching
- LRU eviction policy with 512MB limit

## Data Flow Architecture

### Flow Diagrams

#### Post Creation Flow
```
┌─────────────────┐    ┌──────────────┐    ┌─────────────────┐
│ Client Request  │───▶│PostController│───▶│ PostServiceImpl │
│POST /api/posts  │    │              │    │                 │
└─────────────────┘    └──────────────┘    └─────────────────┘
                                                     │
                                                     ▼
┌─────────────────┐    ┌──────────────┐    ┌─────────────────┐
│   PostgreSQL    │◀───│ Get User     │◀───│ Validate Post   │
│  (User Data)    │    │ Details      │    │   Content       │
└─────────────────┘    └──────────────┘    └─────────────────┘
                                                     │
                                                     ▼
┌─────────────────┐    ┌──────────────┐    ┌─────────────────┐
│   Cassandra     │◀───│ Save Post    │◀───│ Set Timestamps  │
│ (posts table)   │    │              │    │  & Username     │
└─────────────────┘    └──────────────┘    └─────────────────┘
                                                     │
                                                     ▼
                                        ┌─────────────────────┐
                                        │FanoutStrategyFactory│
                                        │                     │
                                        └─────────┬───────────┘
                                                  │
                                    ┌─────────────┴─────────────┐
                                    ▼                           ▼
                       ┌─────────────────────┐    ┌─────────────────────┐
                       │   < 10K Followers   │    │   ≥ 10K Followers   │
                       │ FanoutOnWriteStrategy│    │ FanoutOnReadStrategy │
                       └─────────────────────┘    └─────────────────────┘
                                    │                           │
                                    ▼                           ▼
                       ┌─────────────────────┐    ┌─────────────────────┐
                       │1. Get Followers     │    │1. Save to Cassandra │
                       │   from PostgreSQL   │    │   celebrity_posts   │
                       │2. Add to Redis      │    │2. No immediate      │
                       │   Feed Cache (Async)│    │   fanout            │
                       │3. Process in batches│    │3. Compute on read   │
                       └─────────────────────┘    └─────────────────────┘
                                    │                           │
                                    └─────────────┬─────────────┘
                                                  ▼
                                        ┌─────────────────┐
                                        │   Response:     │
                                        │ Post Created    │
                                        └─────────────────┘
```

#### Feed Retrieval Flow
```
┌─────────────────┐    ┌──────────────┐    ┌─────────────────┐
│ Client Request  │───▶│FeedController│───▶│ FeedServiceImpl │
│GET /api/feed    │    │              │    │                 │
└─────────────────┘    └──────────────┘    └─────┬───────────┘
                                                  │
                        ┌─────────────────────────┼─────────────────────────┐
                        ▼                         ▼                         ▼
               ┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
               │ Get Cached Feed │      │Get Celebrity    │      │                 │
               │     Items       │      │  Feed Items     │      │   FeedMerger    │
               └─────────────────┘      └─────────────────┘      │                 │
                        │                         │              │                 │
                        ▼                         ▼              └─────────────────┘
               ┌─────────────────┐      ┌─────────────────┐               ▲
               │     Redis       │      │   PostgreSQL    │               │
               │ Get Post IDs    │      │Get Celebrity    │               │
               │from user_feed:X │      │Following IDs    │               │
               └─────────────────┘      └─────────────────┘               │
                        │                         │                       │
                        ▼                         ▼                       │
               ┌─────────────────┐      ┌─────────────────┐               │
               │For each Post ID:│      │For each Celebrity│              │
               │                 │      │                 │               │
               │┌───────────────┐│      │┌───────────────┐│               │
               ││Redis Cache?   ││      ││   Cassandra   ││               │
               ││ Yes │   No    ││      ││celebrity_posts││               │
               ││  │  │    │    ││      │└───────────────┘│               │
               ││  ▼  ▼    ▼    ││      └─────────────────┘               │
               ││Redis│Cassandra││              │                         │
               ││Cache│ +Cache  ││              └─────────────────────────┘
               │└───────────────┘│
               └─────────────────┘
                        │
                        └─────────────────────────┐
                                                  ▼
                                        ┌─────────────────┐
                                        │   FeedMerger    │
                                        │                 │
                                        │1. Merge feeds   │
                                        │2. Sort by time  │
                                        │3. Apply limit   │
                                        │4. Pagination    │
                                        └─────────────────┘
                                                  │
                                                  ▼
                                        ┌─────────────────┐
                                        │   Response:     │
                                        │  FeedResponse   │
                                        └─────────────────┘
```

#### System Architecture Overview
```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT LAYER                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
│  │   Mobile    │  │     Web     │  │   Desktop   │  │     API     │       │
│  │    Apps     │  │   Browser   │  │    Apps     │  │   Clients   │       │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘       │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │ HTTP/REST
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            API GATEWAY                                      │
│                     ┌─────────────────────────┐                            │
│                     │     Spring Boot         │                            │
│                     │    REST Controllers     │                            │
│                     └─────────────────────────┘                            │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          SERVICE LAYER                                      │
│ ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐   │
│ │ PostService │    │ FeedService │    │ UserService │    │   Fanout    │   │
│ │             │    │             │    │             │    │ Strategies  │   │
│ │             │    │             │    │             │    │             │   │
│ └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘   │
└─────────────────────┬───────────────────────┬───────────────────┬───────────┘
                      │                       │                   │
                      ▼                       ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        DATABASE LAYER                                       │
│                                                                             │
│ ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐            │
│ │   PostgreSQL    │   │    Cassandra    │   │      Redis      │            │
│ │                 │   │                 │   │                 │            │
│ │ 🔹 Users        │   │ 🔸 Posts        │   │ 🔻 Feed Cache   │            │
│ │ 🔹 Followers    │   │ 🔸 Celebrity    │   │ 🔻 Post Meta    │            │
│ │ 🔹 Relations    │   │   Posts         │   │ 🔻 Sessions     │            │
│ │                 │   │ 🔸 Timelines    │   │                 │            │
│ │ ACID            │   │ High Write      │   │ Fast Cache      │            │
│ │ Transactions    │   │ Throughput      │   │ TTL Support     │            │
│ └─────────────────┘   └─────────────────┘   └─────────────────┘            │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Database Architecture Overview

The system uses a **hybrid database approach** for optimal performance:

| **Database** | **Purpose** | **Tables/Collections** | **Usage Pattern** |
|--------------|-------------|------------------------|-------------------|
| **PostgreSQL** | User management, relationships | `users`, `followers` | ACID transactions, joins for user data |
| **Cassandra** | Post storage | `posts`, `celebrity_posts` | High write throughput, time-series queries |
| **Redis** | Feed caching, post metadata | `user_feed:*`, `post:*` | Fast reads, TTL-based expiration |

### Creating Post Data Flow

#### 1. API Layer (`PostController`)
- **Endpoint**: `POST /api/v1/posts`
- Receives `CreatePostRequest` with user ID, content, media URLs, hashtags
- Validates input and converts to `Post` domain model

#### 2. Service Layer (`PostServiceImpl`)
- **Database**: **PostgreSQL** - Fetches user details to get follower count
- Validates post content (max 280 characters)
- Sets timestamps and username
- **Database**: **Cassandra** - Saves post to `posts` table
- Determines fanout strategy based on follower count

#### 3. Fanout Strategy Selection (`FanoutStrategyFactory`)
- **< 10K followers**: `FanoutOnWriteStrategy` (regular users)
- **≥ 10K followers**: `FanoutOnReadStrategy` (celebrities)

#### 4A. Fan-out on Write Strategy (Regular Users)
- **Database**: **PostgreSQL** - Gets list of follower IDs
- **Database**: **Redis** - Asynchronously adds post ID to each follower's feed cache
- Uses Redis Sorted Sets with timestamp as score
- Processes followers in batches (default: 100) for performance
- Feed is pre-computed for fast reads

#### 4B. Fan-out on Read Strategy (Celebrities)
- **Database**: **Cassandra** - Saves post to `celebrity_posts` table
- Partitioned by `user_id` for efficient queries
- No immediate fanout to followers
- Feed is computed at read time

### Getting Feed Data Flow

#### 1. API Layer (`FeedController`)
- **Endpoint**: `GET /api/v1/feed?userId=X&limit=20&offset=0`
- Validates pagination parameters (max 100 items)

#### 2. Service Layer (`FeedServiceImpl`)
The feed generation uses a **hybrid approach**:

##### Step 1: Get Cached Feed Items (Fan-out on Write)
- **Database**: **Redis** - Retrieves post IDs from user's feed cache using `ZREVRANGE`
- For each post ID:
  - **Database**: **Redis** - First tries to get cached post metadata
  - **Database**: **Cassandra** - Falls back to `posts` table if not cached
  - **Database**: **Redis** - Caches retrieved post for future requests

##### Step 2: Get Celebrity Feed Items (Fan-out on Read)
- **Database**: **PostgreSQL** - Gets list of celebrity IDs that user follows
- For each celebrity:
  - **Database**: **Cassandra** - Queries `celebrity_posts` table partitioned by `user_id`
  - Gets recent posts using clustering key (`created_at`)

##### Step 3: Feed Merging (`FeedMerger`)
- Merges cached posts and celebrity posts
- Sorts by timestamp (newest first)
- Returns top N items based on limit

#### 3. Response Building
- Converts to `FeedResponse` with pagination metadata
- Returns `hasMore` flag for infinite scrolling

### Fanout Strategy Decision Flow
```
                           ┌─────────────────┐
                           │  New Post       │
                           │   Created       │
                           └─────────────────┘
                                    │
                                    ▼
                           ┌─────────────────┐
                           │ Get User        │
                           │ Follower Count  │
                           │ from PostgreSQL │
                           └─────────────────┘
                                    │
                                    ▼
                           ┌─────────────────┐
                           │  Follower       │
                           │ Count ≥ 10K?    │
                           └─────┬───────┬───┘
                                 │       │
                          No     │       │    Yes
                        ◀────────┘       └────────▶
                        │                         │
                        ▼                         ▼
              ┌─────────────────┐       ┌─────────────────┐
              │   REGULAR USER  │       │   CELEBRITY     │
              │                 │       │                 │
              │ Fan-out on Write│       │ Fan-out on Read │
              │    Strategy     │       │    Strategy     │
              └─────────────────┘       └─────────────────┘
                        │                         │
                        ▼                         ▼
              ┌─────────────────┐       ┌─────────────────┐
              │1. Get Followers │       │1. Save Post to  │
              │   from PostgreSQL│      │   celebrity_posts│
              │                 │       │   table         │
              │2. Process in    │       │                 │
              │   Batches (100) │       │2. No Immediate  │
              │                 │       │   Fanout        │
              │3. Add Post ID   │       │                 │
              │   to each       │       │3. Compute Feed  │
              │   Follower's    │       │   at Read Time  │
              │   Redis Feed    │       │                 │
              └─────────────────┘       └─────────────────┘
                        │                         │
                        ▼                         ▼
              ┌─────────────────┐       ┌─────────────────┐
              │ ✅ RESULT:      │       │ ✅ RESULT:      │
              │                 │       │                 │
              │ • Feed Pre-     │       │ • Fast Write    │
              │   computed      │       │   Performance   │
              │ • Fast Read     │       │                 │
              │   Performance   │       │ • Reduced Write │
              │ • Higher Write  │       │   Amplification │
              │   Cost          │       │                 │
              └─────────────────┘       └─────────────────┘
```

### Database Performance Characteristics
```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         DATABASE PERFORMANCE                                │
└─────────────────────────────────────────────────────────────────────────────┘

PostgreSQL (ACID Compliance)          Cassandra (High Throughput)
┌─────────────────────────────┐       ┌─────────────────────────────┐
│ 🔹 Users & Relationships    │       │ 🔸 Posts & Celebrity Posts  │
│                             │       │                             │
│ Read:  ~5,000 QPS          │       │ Write: ~100,000 TPS         │
│ Write: ~2,000 TPS          │       │ Read:  ~50,000 QPS          │
│                             │       │                             │
│ ✓ ACID Transactions        │       │ ✓ Linear Scalability        │
│ ✓ Complex Joins            │       │ ✓ Time-series Optimization  │
│ ✓ Data Consistency         │       │ ✓ Partition Tolerance       │
│                             │       │                             │
│ Used for:                  │       │ Used for:                   │
│ • User metadata            │       │ • Post storage              │
│ • Follow relationships     │       │ • Celebrity post queries    │
│ • Authentication           │       │ • Timeline generation       │
└─────────────────────────────┘       └─────────────────────────────┘
                    │                              │
                    └──────────┬───────────────────┘
                              │
                              ▼
                 ┌─────────────────────────────┐
                 │       Redis (Cache)         │
                 │                             │
                 │ Read:  ~500,000 QPS        │
                 │ Write: ~200,000 TPS        │
                 │                             │
                 │ ✓ Sub-millisecond latency  │
                 │ ✓ TTL Support              │
                 │ ✓ Data Structures          │
                 │                             │
                 │ Used for:                  │
                 │ • Pre-computed feeds       │
                 │ • Post metadata cache      │
                 │ • Session management       │
                 └─────────────────────────────┘
```

### Key Performance Optimizations

1. **Hybrid Fanout**: Balances write/read performance based on user popularity
2. **Asynchronous Processing**: Fan-out operations don't block API responses
3. **Batch Processing**: Followers processed in configurable batches (default: 100)
4. **Multi-level Caching**: Redis caches both feed lists and post metadata
5. **Partitioned Storage**: Cassandra tables partitioned for efficient queries
6. **TTL Management**: Redis entries expire automatically (default: 1 hour)

## Common Commands

### Service Management

```bash
# Start services
./deploy.sh start

# Stop services
./deploy.sh stop

# Restart services
./deploy.sh restart

# View logs
./deploy.sh logs postgres
./deploy.sh logs cassandra
./deploy.sh logs redis
```

### Cleanup

To remove all containers and data:

```bash
./deploy.sh clean
```



## Sample Data

The system initializes with:
- 5 regular users with follower relationships
- 3 celebrity users (10K+ followers)
- Sample posts in PostgreSQL and Cassandra

## Performance Targets

- Feed generation: < 100ms (p95)
- Post creation (regular): < 200ms
- Post creation (celebrity): < 50ms
- Feed retrieval: < 150ms (p95)

## License

This is a demonstration project for learning distributed systems architecture.

## Support

For issues or questions, refer to:
- [SYSTEM_DESIGN.md](SYSTEM_DESIGN.md) - Architecture details
- [CLAUDE.md](CLAUDE.md) - Development guidelines
