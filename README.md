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

## Development Guidelines

See [CLAUDE.md](CLAUDE.md) for:
- SOLID principles implementation
- Clean code standards
- Package structure
- Testing requirements
- Code review checklist

See [SYSTEM_DESIGN.md](SYSTEM_DESIGN.md) for:
- Detailed architecture
- Database schemas
- API design
- Performance targets
- Scalability considerations

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
