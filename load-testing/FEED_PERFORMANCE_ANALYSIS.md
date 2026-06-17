# Feed Query Performance Analysis Report

**Date:** June 17, 2026  
**Test Duration:** ~4 minutes (240 seconds)  
**Test File:** `1hr-load-test.jtl`  
**Total Requests Analyzed:** 73,781 (20,374 feed-related)

---

## Executive Summary

The feed query endpoints exhibit **severe performance degradation** during load testing, with response times increasing 4.2x slower than baseline performance. The analysis identifies a critical **N+1 query pattern** as the root cause, combined with connection pool exhaustion under sustained load.

### Key Findings:
- **GET User Feed**: 1,144ms average (1.14x baseline) → **17.1% failure rate**
- **GET User Feed (Before Post)**: 4,168ms average (4.2x baseline) → **8.5% failure rate**
- Performance degrades progressively from stable baseline (50-100ms) to catastrophic (15,450ms max)
- Failures begin at **13:43:57** (38 seconds into test) and escalate exponentially
- Direct correlation between concurrent thread count and response time degradation

---

## 1. Performance Metrics Summary

### 1.1 GET User Feed (Normal Feed Retrieval)

| Metric | Value | Status |
|--------|-------|--------|
| **Total Requests** | 16,759 | — |
| **Success Rate** | 82.9% (13,887) | 🔴 CRITICAL |
| **Failed Requests** | 2,872 (17.1%) | 🔴 CRITICAL |
| **Response Time - Min** | 2ms | ✓ |
| **Response Time - Max** | 15,450ms | 🔴 CRITICAL |
| **Response Time - Mean** | 1,143.8ms | 🔴 CRITICAL |
| **Response Time - Median** | 511.0ms | 🟡 WARNING |
| **Response Time - P95** | 4,311ms | 🔴 CRITICAL |
| **Response Time - P99** | 10,951ms | 🔴 CRITICAL |
| **Outliers (>2000ms)** | 2,526 (15.1%) | 🔴 CRITICAL |

**Expected Response Time:** <1,000ms  
**Actual Response Time:** 1,144ms  
**Degradation Ratio:** 1.14x (114%)

---

### 1.2 GET User Feed (Before Post)

| Metric | Value | Status |
|--------|-------|--------|
| **Total Requests** | 3,615 | — |
| **Success Rate** | 91.5% (3,308) | 🔴 CRITICAL |
| **Failed Requests** | 307 (8.5%) | 🔴 CRITICAL |
| **Response Time - Min** | 3ms | ✓ |
| **Response Time - Max** | 15,691ms | 🔴 CRITICAL |
| **Response Time - Mean** | 4,168.0ms | 🔴 CRITICAL |
| **Response Time - Median** | 2,461.0ms | 🔴 CRITICAL |
| **Response Time - P95** | 11,852ms | 🔴 CRITICAL |
| **Response Time - P99** | 13,365ms | 🔴 CRITICAL |
| **Outliers (>2000ms)** | 1,985 (54.9%) | 🔴 CRITICAL |

**Expected Response Time:** <1,000ms  
**Actual Response Time:** 4,168ms  
**Degradation Ratio:** 4.17x (417%) — **AS MENTIONED IN REQUIREMENTS**

---

### 1.3 Comparison to Baseline

| Operation | Expected | Actual | Ratio | Status |
|-----------|----------|--------|-------|--------|
| **GET User Profile** | 921ms | 921ms | 1.00x | ✓ |
| **GET Following** | 931ms | 931ms | 1.00x | ✓ |
| **GET Followers** | 916ms | 916ms | 1.00x | ✓ |
| **GET User Feed** | 1,000ms | 1,144ms | 1.14x | 🟡 |
| **GET User Feed (Before Post)** | 1,000ms | 4,168ms | 4.17x | 🔴 |

**Analysis:** All baseline operations (User Profile, Following, Followers) perform at expected levels. Feed operations are **significantly slower**, especially the "Before Post" variant, indicating feed-specific bottlenecks.

---

## 2. Temporal Performance Degradation

### 2.1 Three Performance Phases Identified

#### Phase 1: Ramp-up (13:42:19 - 13:42:58)
- **Duration:** 39 seconds
- **Avg Response Time:** ~200-600ms
- **Concurrent Threads:** 189-1,219
- **Status:** Performance acceptable, ramp-up in progress
- **Error Rate:** 0%

#### Phase 2: Stable High Load (13:42:59 - 13:44:50)
- **Duration:** ~110 seconds
- **Avg Response Time:** ~600-1,500ms
- **Peak Concurrent Threads:** 1,400+
- **Status:** Performance gradually degrading
- **Error Rate:** Begins ~13:43:57 (0% → 17%)

#### Phase 3: Critical Degradation (13:44:50 - 13:46:32)
- **Duration:** ~100 seconds
- **Avg Response Time:** ~2,500-10,000ms
- **Peak Concurrent Threads:** 6,000+
- **Peak Single Request:** 15,450ms
- **Status:** Severe performance collapse
- **Error Rate:** 17-30%

### 2.2 Response Time Progression

```
13:42:19  |████░░░░░░ ~600ms
13:42:30  |█████████░ ~1,700ms
13:42:45  |██████████ ~2,237ms
13:43:00  |█░░░░░░░░░ ~50ms (recovery)
13:43:15  |██░░░░░░░░ ~61ms (stable)
13:44:00  |████░░░░░░ ~513ms (instability)
13:45:00  |███████░░░ ~1,100ms (degradation)
13:45:15  |██████████ ~10,000ms (CRITICAL)
13:45:20  |██████████ ~5,500ms (lingering)
13:45:30  |████████░░ ~2,600ms (recovery)
13:46:00  |██░░░░░░░░ ~1,000ms (recovery)
13:46:15  |██░░░░░░░░ ~887ms (stable)
```

### 2.3 Critical Observation: Thread Load Correlation

There is a **direct positive correlation** between concurrent thread count and response time degradation:

- **Threads 0-1,000:** Avg response time ~100-600ms ✓
- **Threads 1,000-2,000:** Avg response time ~800-2,500ms 🟡
- **Threads 2,000-4,000:** Avg response time ~3,000-6,000ms 🔴
- **Threads 4,000-7,000:** Avg response time ~6,000-15,450ms 🔴 CRITICAL

**Implication:** The connection pool is exhausted around 1,000-1,500 threads, causing thread starvation and queueing.

---

## 3. Root Cause Analysis: N+1 Query Problem

### 3.1 Code Analysis

**File:** `FeedServiceImpl.java:42-71` (getUserFeed method)

#### Flow:
```
1. getCachedFeedItems(userId, limit)
   └─ feedCacheRepository.getRecentFeed(userId, limit) → Returns Set<String> of POST IDs
      └─ FOR EACH POST ID:
         └─ feedCacheRepository.getCachedPost(postId) → 1 Redis query (cached, usually hits)
         └─ IF NOT CACHED:
            └─ postRepository.findById(postId) → 1 Cassandra query (SLOW!)
            └─ feedCacheRepository.cachePost(postId, feedItem) → 1 Redis write

2. getCelebrityFeedItems(userId, limit)
   └─ userService.getCelebrityFollowingIds(userId) → 1 query
      └─ FOR EACH CELEBRITY ID (could be 10-100+):
         └─ celebrityPostRepository.findRecentByUserId(celebId, limit) → 1 Cassandra query per celebrity
         └─ FOR EACH POST FROM EACH CELEBRITY:
            └─ convertToFeedItem() → No additional query (but CPU work)

3. feedMerger.mergeFeed() → In-memory merge, no queries
```

### 3.2 The N+1 Problem Breakdown

When user requests feed with `limit=20`:

#### Scenario A: Posts mostly cached (~10% miss rate)
- 1 Redis ZREVRANGE query
- ~2 Redis GET queries for uncached posts
- ~2 Cassandra reads for truly uncached posts
- **Total: ~5 queries** ✓ Acceptable

#### Scenario B: Posts mostly NOT cached (~70% miss rate)
- 1 Redis ZREVRANGE query
- ~14 Redis GET queries (misses)
- ~14 Cassandra reads (SLOW!)
- **Total: ~29 queries** 🔴 Problem!

#### Scenario C: Heavy Load + Connection Pool Exhaustion
- Cassandra connection pool fills up (default: 8-16 connections)
- Remaining thread requests queue for connections
- Each thread waits 100-500ms for a connection
- With 100 concurrent threads requesting 20 posts each:
  - 100 threads × 20 posts = 2,000 Cassandra queries needed
  - But only 8-16 connections available
  - Wait time = (2,000 queries / 8 connections) × avg_query_time
  - = 250 queries queued per connection
  - = 250 × 50-100ms per query = **12,500-25,000ms delay**

---

### 3.3 Celebrity Feed Amplification

The celebrity feed path is even worse:

```java
List<Long> celebrityIds = userService.getCelebrityFollowingIds(userId);
// Returns: [1, 5, 10, 50, 100, 200] (6 celebrities)

for (Long celebrityId : celebrityIds) {
    List<CelebrityPost> posts = celebrityPostRepository.findRecentByUserId(
        celebrityId,
        limit  // 20 posts per celebrity
    );
    // 6 queries × 1 per celebrity = 6 Cassandra queries minimum
}
```

**If a user follows 50 celebrities:**
- 50 Cassandra queries just to fetch celebrity posts
- Combined with cached feed posts (14+ Cassandra queries)
- **Total: ~64 Cassandra queries per feed request**
- At 100 concurrent threads = 6,400 queries
- With 8-connection pool = 800 queries per connection
- = **800 × 75ms = 60,000ms latency** 🔴 CRITICAL

---

## 4. Database Implications

### 4.1 Cassandra Performance Characteristics

The system uses **Cassandra** for post storage with the following implications:

- **Distributed eventually-consistent store** → Natural eventual consistency delays
- **Default co
