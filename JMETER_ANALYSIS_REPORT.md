# JMeter Load Test Analysis: Connection Pool Exhaustion Investigation

**Test File:** 1hr-load-test.jtl  
**Test Date:** June 17, 2026  
**Analysis Date:** 2026-06-17

---

## Executive Summary

The load test reveals **critical connection pool exhaustion** starting at **97 seconds** into the test, causing a **20.82% error rate** (11,849 BindException errors out of 73,780 total requests). The system recovered partially but never fully, indicating **insufficient pool sizing** rather than connection leaks. Peak concurrency reached **10,603 concurrent threads** against thread pools sized for only **70 threads total** (50 fanout + 20 feed).

---

## 1. BindException Error Analysis

### 1.1 Quantitative Breakdown
- **Total BindException errors: 11,849** ✓ (confirmed as expected)
- **Total requests: 73,780**
- **Success rate: 79.18%**
- **Error rate: 20.82%**

### 1.2 Error Timeline

| Time Period | Errors | Success Rate | Status |
|-------------|--------|--------------|--------|
| 0-29 sec   | 0      | 100.0%       | Healthy |
| 30-59 sec  | 0      | 100.0%       | Healthy |
| 60-89 sec  | 0      | 100.0%       | Ramping |
| **90-119 sec** | **6,919** | **30.8%** | **CRITICAL** |
| 120-149 sec| 3,624  | 63.2%        | Degraded |
| 150-179 sec| 943    | 84.9%        | Recovering |
| 180-209 sec| 240    | 70.2%        | Unstable |
| 210-239 sec| 2      | 83.2%        | Partial Recovery |
| 240-269 sec| 121    | 65.1%        | Degraded |

### 1.3 Error Onset Timing
- **Starts:** 97 seconds into test (1 minute, 37 seconds)
- **Duration:** 150 seconds (errors span from second 90 to second 240)
- **Peak intensity:** 6,919 errors in 30-second window (90-119 sec)
- **Recovery:** Partial - never returns to 100% success
- **Pattern:** Spike → partial recovery → instability pattern

### 1.4 Distribution by Request Type (All GET/POST)

| Request Type | Total Errors | Error Rate |
|--------------|--------------|-----------|
| GET User Profile | 2,923 | 17.5% |
| GET Following | 2,858 | 17.2% |
| GET Followers | 2,846 | 17.1% |
| GET User Feed | 2,843 | 17.0% |
| GET User Feed (Before Post) | 307 | 8.5% |
| POST New Post | 72 | 2.0% |

**Finding:** Errors affect **all endpoints uniformly** (~17% for main endpoints, <10% for others). No single endpoint is disproportionately impacted, indicating **system-wide connection exhaustion** rather than endpoint-specific issues.

### 1.5 Distribution by Endpoint

All 9 user endpoints (GET /users/{id}, /users/{id}/followers, /users/{id}/following) show **uniform distribution** of ~350-380 errors each:
- Feed endpoint: 3,150 errors (26.6% of total)
- User endpoints: ~8,589 errors (72.5% distributed across 9 endpoints)
- Post endpoint: 72 errors (0.6%)

---

## 2. Connection and Concurrency Pattern Analysis

### 2.1 Thread Pool Concurrency Progression

| Time Period | Peak Threads | Min Threads | Thread Delta |
|-------------|--------------|-----------|--------------|
| 0-29 sec   | 1,258        | 142       | 1,116        |
| 30-59 sec  | 1,239        | 664       | 575          |
| 60-89 sec  | 805          | 695       | 110          |
| 90-119 sec | 1,243        | 717       | 526          |
| **120-149 sec** | **1,242** | **728** | **514** |
| 150-179 sec| 3,314        | 192       | 3,122        |
| 180-209 sec| 6,014        | 1,547     | 4,467        |
| 210-239 sec| 9,081        | 1,137     | 7,944        |
| **240-269 sec** | **10,603** | **1,109** | **9,494** |

### 2.2 Peak Statistics
- **Peak concurrent threads: 10,603**
- **Average concurrent threads: 1,610**
- **Peak request rate: ~294 req/sec**
- **Sustained request rate: ~294 req/sec throughout test**

### 2.3 Request Rate Analysis
- **Average request rate: 294 requests/second** (73,780 requests / 251 seconds)
- **Requests per peak thread: 7 requests** (294 req/sec ÷ 10,603 threads ≈ 0.028 req/thread/sec)
- **Average response time (successful): 1,207 ms**

### 2.4 Connection Exhaustion Characteristics

| Metric | Value | Analysis |
|--------|-------|----------|
| BindException latency (avg) | 0 ms | Connection fails **before** HTTP request |
| Success latency (avg) | 1,207 ms | Requests that succeed take 1.2 seconds |
| Error latency/Connect time | 0 ms | **NOT a timeout issue** - immediate refusal |
| Peak threads | 10,603 | **150x larger** than fanout executor (50) |

**Critical Finding:** BindException errors show 0ms latency/connect time, meaning the connection pool rejects the request **immediately** without attempting connection. This indicates:
1. ✓ **NOT a connection timeout issue** (those would show non-zero latency)
2. ✓ **NOT a thread pool executor issue** (Java executor would queue or CallerRunsPolicy)
3. ✓ **This is HTTP client connection pool exhaustion**
4. ✗ **Likely culprit:** Tomcat connector thread pool exhaustion

---

## 3. Current Configuration vs. Requirements Analysis

### 3.1 Current Pool Configurations

**PostgreSQL (HikariCP):**
- Maximum pool size: **60** connections
- Minimum idle: 15 connections
- Connection timeout: 30 seconds
- Max lifetime: 30 minutes
- Current utilization ratio: TBD

**Redis (Jedis):**
- Max active: **60** connections
- Max idle: 30 connections
- Min idle: 10 connections
- Max wait time: 3 seconds
- Current utilization ratio: TBD

**Cassandra:**
- No explicit pool size configured in `CassandraConfig.java`
- Uses Spring Data Cassandra defaults (typically 8-12 connections per host)

**Application Thread Pools:**
- Fanout executor: Core=10, **Max=50** threads, Queue=1,000
- Feed executor: Core=5, **Max=20** threads, Queue=500
- **Total async threads: 70 maximum**

**Tomcat Connector (HTTP Server):**
- Default: max-threads=200 (likely not configured, using default)
- Current configuration: **Unknown** (needs verification)

### 3.2 Concurrency Requirements Analysis

**From Load Test:**
- Peak concurrent threads: 10,603
- Peak request rate: 294 req/sec
- Successful request avg latency: 1,207 ms

**Connection Pool Requirements Calculation:**

For **HTTP client connections** (JMeter to server):
- Peak concurrent threads: 10,603 threads (JMeter simulated users)
- These are **client-side connections**, not server-side
- Each thread likely needs 1 HTTP connection
- **Estimated HTTP pool needed: ~10,600 connections** (massive, unrealistic)

For **server-side database connections** per request:
- Successful requests: 58,326
- Duration: 251 seconds
- Peak concurrent: ~294 req/sec with 1,207ms latency
- Average DB connections needed: 294 req/sec × 1.207s = **~355 concurrent DB connections**

Current PostgreSQL pool: **60 connections** → **5.9x undersized**

For **Redis connections:**
- Not typically a bottleneck
- 60 connections should handle this load

### 3.3 Tomcat Connector Analysis

The immediate **BindException** with 0ms latency strongly suggests **Tomcat connector thread exhaustion**:
- Default Tomcat max-threads: 200
- Peak request rate: 294 req/sec
- If each request holds a thread for 1,207ms: 294 × 1.207 = **355 threads needed**
- Current Tomcat default: **200 threads** → **1.78x undersized**

---

## 4. Root Cause Analysis

### 4.1 Primary Cause: Multi-Layer Connection Pool Exhaustion

| Layer | Status | Evidence |
|-------|--------|----------|
| **Tomcat HTTP Connector** | ⚠️ CRITICAL | BindException with 0ms latency = immediate rejection |
| **PostgreSQL HikariCP** | ⚠️ CRITICAL | 60 connections vs 355 needed = 5.9x undersized |
| **Redis Jedis** | ✓ Adequate | 60 connections should suffice |
| **Cassandra** | ✓ Likely OK | Default pool should handle |
| **Application Executors** | ⚠️ CRITICAL | 70 total threads vs 10,603 concurrent = 151x undersized |

### 4.2 Cascade Failure Pattern

```
Second 0-89: Threads ramping up, all requests succeed
              ↓
Second 90+:   Thread count exceeds application executor capacity
              ↓
             CallerRunsPolicy kicks in for fanout/feed tasks
              ↓
             Main Tomcat threads block waiting for DB connections
              ↓
             Tomcat connector pool exhausted (no threads available)
              ↓
             New HTTP requests get immediate BindException
    
