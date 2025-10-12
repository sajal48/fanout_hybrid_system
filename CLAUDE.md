# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a hybrid fanout Twitter-like feed system designed to efficiently handle both regular users (< 10K followers) and celebrity users (≥ 10K followers) using different fanout strategies.

**Current Status**: Design phase - no code implementation yet. See SYSTEM_DESIGN.md for complete architectural specifications.

## Planned Technology Stack

- **Backend**: Spring Boot 3.x with Java 17
- **Build Tool**: Maven
- **Databases**:
  - PostgreSQL 15+ (user metadata, follower relationships)
  - Apache Cassandra 4.x (posts, celebrity feeds, timelines)
  - Redis 7+ (caching, user feeds for regular users)

## Docker Development Environment

### Quick Start

```bash
chmod +x deploy.sh
./deploy.sh start
```

### Docker Commands

All database services are containerized using Docker Compose. Use the deployment script for easy management:

**Start all services**:
```bash
./deploy.sh start
```

**Stop all services**:
```bash
./deploy.sh stop
```

**Check service status**:
```bash
./deploy.sh status
```

**View logs**:
```bash
# All services
./deploy.sh logs

# Specific service
./deploy.sh logs postgres
./deploy.sh logs cassandra
./deploy.sh logs redis
```

**Connect to databases**:
```bash
# PostgreSQL
./deploy.sh postgres

# Cassandra CQL Shell
./deploy.sh cassandra

# Redis CLI
./deploy.sh redis
```

**Clean everything** (removes all containers and volumes):
```bash
./deploy.sh clean
```

### Manual Docker Compose Commands

If you need to use Docker Compose directly:

```bash
# Start services
docker compose up -d

# Stop services
docker compose down

# View logs
docker compose logs -f [service_name]

# Check status
docker compose ps

# Remove volumes (clean data)
docker compose down -v
```

### Database Connection Details

**PostgreSQL**:
- Host: `localhost`
- Port: `5432`
- Database: `twitter_feed`
- Username: `twitter_admin`
- Password: `twitter_password_123`
- JDBC URL: `jdbc:postgresql://localhost:5432/twitter_feed`

**Cassandra**:
- Host: `localhost`
- Port: `9042` (CQL)
- Keyspace: `twitter_feeds`
- Contact points: `127.0.0.1`

**Redis**:
- Host: `localhost`
- Port: `6379`
- No password (development only)
- Max Memory: `512mb` with LRU eviction

### Database Initialization

Database schemas and sample data are automatically initialized on first startup:

- **PostgreSQL**: `docker/postgres/init.sql`
  - Creates `users` and `followers` tables
  - Sets up indexes and triggers
  - Inserts sample users and relationships
  - Automatically maintains follower counts

- **Cassandra**: `docker/cassandra/init.cql`
  - Creates `twitter_feeds` keyspace
  - Creates tables: `posts`, `celebrity_posts`, `user_timeline`, `feed_cache`, etc.
  - Inserts sample posts

- **Redis**: `docker/redis/redis.conf`
  - Configured for caching with LRU eviction
  - AOF persistence enabled
  - Optimized for low-latency operations

## Core Architecture Concepts

### Hybrid Fanout Strategy

The system uses two distinct approaches based on follower count:

1. **Fan-out on Write** (< 10K followers):
   - Pre-compute feeds for all followers when a post is created
   - Store in Redis sorted sets: `user_feed:{user_id}`
   - Fast reads, higher write overhead

2. **Fan-out on Read** (≥ 10K followers):
   - Mark user as celebrity (`is_celebrity = true` in PostgreSQL)
   - Store posts in `celebrity_posts` table in Cassandra
   - Compute feeds on-demand during read operations
   - Lower write overhead, slightly higher read latency

### Database Responsibilities

**PostgreSQL**:
- `users` table: user metadata, follower counts, celebrity status
- `followers` table: follower/following relationships
- Indexed on: follower_id, following_id, username, is_celebrity

**Cassandra** (keyspace: `twitter_feeds`):
- `posts` table: all posts with metadata (partition key: post_id)
- `user_timeline` table: timeline for celebrity users (partition key: user_id, clustering: created_at DESC)
- `celebrity_posts` table: celebrity posts for efficient fan-out on read (partition key: user_id, clustering: created_at DESC)

**Redis**:
- `user_feed:{user_id}`: Sorted set of post IDs (score = timestamp)
- `user:{user_id}`: Hash of user metadata
- `post:{post_id}`: Hash of post data
- `celebrity_users`: Set of celebrity user IDs
- `follower_count:{user_id}`: String value of follower count

### Critical Thresholds

- **Celebrity threshold**: 10,000 followers
- When a user crosses this threshold, toggle `is_celebrity` flag and change fanout strategy

## Coding Standards

### SOLID Principles

This codebase strictly adheres to SOLID principles:

#### Single Responsibility Principle (SRP)
- Each class should have one, and only one, reason to change
- **Example**: Separate `FanoutOnWriteStrategy` and `FanoutOnReadStrategy` classes instead of one monolithic fanout handler
- Services should focus on orchestration, not data access logic (use repositories)
- DTOs for data transfer, entities for persistence, domain models for business logic

#### Open/Closed Principle (OCP)
- Classes should be open for extension but closed for modification
- **Example**: Use `FanoutStrategy` interface with multiple implementations (`FanoutOnWriteStrategy`, `FanoutOnReadStrategy`)
- Use strategy pattern for fanout logic selection based on follower count
- Avoid large switch/if-else blocks; prefer polymorphism

#### Liskov Substitution Principle (LSP)
- Subtypes must be substitutable for their base types
- **Example**: Any `FanoutStrategy` implementation should work without changing client code
- Repository implementations should honor their interface contracts
- Avoid throwing unexpected exceptions in implementations

#### Interface Segregation Principle (ISP)
- Clients should not be forced to depend on interfaces they don't use
- **Example**: Separate `PostRepository`, `UserRepository`, `FeedRepository` rather than one giant `DataRepository`
- Split large service interfaces into smaller, focused ones (e.g., `FollowerManagement`, `CelebrityStatusManagement`)
- Use specific DTOs for different endpoints instead of reusing the same large DTO

#### Dependency Inversion Principle (DIP)
- Depend on abstractions, not concretions
- **Example**: Services depend on repository interfaces, not concrete implementations
- Use Spring's `@Autowired` with interfaces, not concrete classes
- Inject `FanoutStrategy` interface, resolved at runtime based on user type

### Clean Code Principles

#### Naming Conventions
- **Classes**: Nouns in PascalCase (`PostService`, `FeedGenerator`, `CelebrityDetector`)
- **Interfaces**: Descriptive names without "I" prefix (`FanoutStrategy`, `PostRepository`)
- **Methods**: Verbs in camelCase (`createPost()`, `getFeed()`, `determineStrategy()`)
- **Variables**: Descriptive camelCase (`followerCount`, `celebrityThreshold`, `postId`)
- **Constants**: UPPER_SNAKE_CASE (`CELEBRITY_THRESHOLD = 10_000`, `MAX_FEED_SIZE = 100`)
- **Boolean variables/methods**: Use `is`, `has`, `can` prefixes (`isCelebrity()`, `hasFollowers()`, `canFanout()`)

#### Method Design
- **Small methods**: Each method should do one thing (max 20 lines ideally)
- **Few parameters**: Maximum 3-4 parameters; use DTOs/builder pattern for more
- **No side effects**: Methods named `get*` or `is*` should not modify state
- **Command-Query Separation**: Methods either return data (query) or change state (command), not both
- **Fail fast**: Validate inputs at the start of methods

#### Error Handling
- Use custom exceptions: `CelebrityThresholdExceededException`, `FeedGenerationException`, `InvalidPostException`
- Never catch and ignore exceptions silently
- Use `@ControllerAdvice` for global exception handling
- Log with context (user_id, post_id, action) using SLF4J
- Return meaningful error responses with proper HTTP status codes

#### Comments and Documentation
- Code should be self-documenting through good naming
- Use JavaDoc for public APIs, interfaces, and complex algorithms
- Explain "why", not "what" in comments
- Keep comments up-to-date or remove them
- Document non-obvious business rules (e.g., 10K threshold rationale)

#### Class Organization
```
1. Static constants
2. Static variables
3. Instance variables (private)
4. Constructors
5. Public methods
6. Protected/package-private methods
7. Private methods
8. Inner classes
```

### Architecture Patterns

#### Service Layer Architecture

Apply SOLID principles to service design:

**Core Services**:
1. **PostService**: Orchestrates post creation, delegates to strategies
2. **FeedService**: Retrieves and merges feeds from multiple sources
3. **UserService**: Manages user metadata and follower relationships
4. **CelebrityStatusService**: Monitors and updates celebrity status

**Strategy Pattern for Fanout**:
```java
interface FanoutStrategy {
    void executeFanout(Post post, List<Long> followerIds);
}

class FanoutOnWriteStrategy implements FanoutStrategy {
    // Pre-compute feeds for all followers
}

class FanoutOnReadStrategy implements FanoutStrategy {
    // Store in celebrity table only
}

class FanoutStrategyFactory {
    FanoutStrategy getStrategy(long followerCount) {
        return followerCount < CELEBRITY_THRESHOLD
            ? fanoutOnWriteStrategy
            : fanoutOnReadStrategy;
    }
}
```

**Repository Pattern**:
- `UserRepository` (PostgreSQL - Spring Data JPA)
- `FollowerRepository` (PostgreSQL - Spring Data JPA)
- `PostRepository` (Cassandra - Spring Data Cassandra)
- `CelebrityPostRepository` (Cassandra - Spring Data Cassandra)
- `FeedCacheRepository` (Redis - Spring Data Redis)

**DTO Pattern**:
- `CreatePostRequest`, `PostResponse`
- `FeedRequest`, `FeedResponse`
- `UserProfileDto`, `FollowerDto`
- Never expose entities directly in REST APIs

### Feed Generation Flow

1. `PostService.createPost()` receives request
2. Validate input (SRP: separate validator class)
3. `UserService.getFollowerCount()` checks count
4. `FanoutStrategyFactory.getStrategy()` selects strategy (OCP)
5. `FanoutStrategy.executeFanout()` performs fanout **asynchronously** (polymorphism)
6. Return appropriate response DTO immediately (post saved, fanout in background)

### Asynchronous Fanout Pattern

The fan-out on write strategy uses async execution to prevent blocking during post creation:

**Key Implementation Details**:
- `@Async("fanoutExecutor")` annotation on async methods
- Dedicated thread pool: 10 core threads, 50 max threads, 1000 queue capacity
- Batch processing: Followers split into batches (default 100 per batch)
- Parallel batch execution using `CompletableFuture.allOf()`
- Non-blocking post creation: API returns immediately after scheduling fanout

**Configuration** (`application.yml`):
```yaml
feed:
  fanout:
    batch-size: 100              # Followers per batch
    parallel-batch-processing: true
```

**Thread Pool Configuration** (`AsyncConfig.java`):
- **fanoutExecutor**: High concurrency for fanout operations (50 max threads)
- **feedExecutor**: Moderate concurrency for feed generation (20 max threads)
- **Rejection Policy**: CallerRunsPolicy (caller thread executes if pool full)
- **Graceful shutdown**: Waits 60s for tasks to complete on shutdown

**Performance Benefits**:
- **Synchronous**: 9,999 followers = ~3-5 seconds blocking
- **Async with batching**: Post creation returns in < 50ms, fanout completes in background
- **Parallel batches**: 9,999 followers in 100 batches = ~500ms total with 10 parallel threads

**Flow**:
```
POST /posts
    ↓
PostService.createPost()
    ↓
Save post to Cassandra (blocking)
    ↓
FanoutOnWriteStrategy.executeFanout() (returns immediately)
    ↓
Response to client ✓
    ↓
[Background] executeFanoutAsync()
    ↓
Split 9,999 followers into 100 batches
    ↓
Process batches in parallel (10 threads)
    ↓
Each batch writes to Redis
    ↓
Complete in ~500ms (background)
```

### Feed Retrieval Flow

1. `FeedService.getUserFeed()` receives request
2. `UserService.getCelebrityFollowing()` gets celebrity IDs
3. `FeedCacheRepository.getUserFeed()` retrieves cached posts (SRP)
4. `CelebrityPostRepository.getRecentPosts()` queries celebrity posts (SRP)
5. `FeedMerger.merge()` combines and sorts (SRP)
6. `PaginationService.paginate()` applies cursor logic (SRP)
7. Map entities to `FeedResponse` DTO

## Performance Targets

- Feed generation: < 100ms (p95)
- Post creation (write fanout): < 200ms
- Post creation (celebrity): < 50ms
- Feed retrieval: < 150ms (p95)

## Database Schema References

See SYSTEM_DESIGN.md lines 98-180 for complete PostgreSQL and Cassandra schemas.

## Testing Standards

### Unit Testing
- Test coverage minimum: 80% for service layer, 70% overall
- Use JUnit 5 and Mockito
- Follow AAA pattern (Arrange, Act, Assert)
- One assertion concept per test method
- Use descriptive test names: `shouldReturnFanoutOnWriteStrategy_whenFollowerCountBelowThreshold()`

### Integration Testing
- Use `@SpringBootTest` for full context tests
- Use Testcontainers for PostgreSQL, Cassandra, Redis
- Test repository layer with real database interactions
- Test API endpoints with `@WebMvcTest` or `MockMvc`

### Test Organization
```
src/test/java/
  ├── unit/
  │   ├── service/
  │   ├── strategy/
  │   └── validator/
  └── integration/
      ├── repository/
      └── api/
```

## Package Structure

Organize code by feature/domain, not layer:

```
com.twitter.feed/
  ├── post/
  │   ├── controller/
  │   │   └── PostController.java
  │   ├── service/
  │   │   ├── PostService.java
  │   │   └── PostServiceImpl.java
  │   ├── repository/
  │   │   ├── PostRepository.java
  │   │   └── CelebrityPostRepository.java
  │   ├── model/
  │   │   ├── Post.java (entity)
  │   │   └── PostDto.java
  │   └── exception/
  │       └── InvalidPostException.java
  ├── feed/
  │   ├── controller/
  │   ├── service/
  │   │   ├── FeedService.java
  │   │   ├── FeedMerger.java
  │   │   └── PaginationService.java
  │   ├── repository/
  │   │   └── FeedCacheRepository.java
  │   └── model/
  ├── user/
  │   ├── controller/
  │   ├── service/
  │   │   ├── UserService.java
  │   │   └── CelebrityStatusService.java
  │   ├── repository/
  │   │   ├── UserRepository.java
  │   │   └── FollowerRepository.java
  │   └── model/
  └── fanout/
      ├── strategy/
      │   ├── FanoutStrategy.java (interface)
      │   ├── FanoutOnWriteStrategy.java
      │   ├── FanoutOnReadStrategy.java
      │   └── FanoutStrategyFactory.java
      └── config/
          └── FanoutConfig.java
```

## Configuration Management

### Application Properties
- Use `application.yml` for readability
- Separate profiles: `application-dev.yml`, `application-prod.yml`
- Externalize configuration values (thresholds, timeouts, pool sizes)
- Use `@ConfigurationProperties` for type-safe configuration

### Example Configuration
```yaml
feed:
  celebrity-threshold: 10000
  max-feed-size: 100
  cache-ttl-seconds: 3600

spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  data:
    cassandra:
      keyspace-name: twitter_feeds
      contact-points: localhost
      port: 9042
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
```

## API Endpoints (Planned)

- `POST /api/v1/posts` - Create new post
- `GET /api/v1/feed?limit=20&cursor={token}` - Retrieve user feed
- `POST /api/v1/users/{userId}/follow` - Follow user
- `DELETE /api/v1/users/{userId}/follow` - Unfollow user

## Code Review Checklist

Before committing code, verify:
- [ ] SOLID principles applied appropriately
- [ ] Methods are small and focused (SRP)
- [ ] No code duplication (DRY principle)
- [ ] Meaningful variable and method names
- [ ] Proper error handling with custom exceptions
- [ ] Unit tests written with >80% coverage for services
- [ ] No magic numbers (use named constants)
- [ ] DTOs used instead of exposing entities
- [ ] Repository methods follow naming conventions (`findBy*`, `save`, `delete`)
- [ ] Logging added for important operations (post creation, fanout execution)
- [ ] No database queries in loops (N+1 problem)
- [ ] Proper transaction boundaries with `@Transactional`
