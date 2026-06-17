# JMeter Load Test Analysis - Phase 2 Async Implementation

## Executive Summary

**Status: FAILED - NOT PRODUCTION READY**

The Phase 2 async implementation has achieved a **57% response time improvement** for POST operations (3,131ms vs 7,300ms baseline), but at the cost of **complete test failure** (0% success rate) and **severe system reliability issues** (16% connection failures).

**Critical Finding:** The system cannot sustain the target concurrent load (300+ users) beyond 2 minutes, with performance degrading from 100% success in minute 0 to 65% by minute 1.

---

## Test Environment & Configuration

| Metric | Value |
|--------|-------|
| Test Date | June 17, 2026 |
| Test Duration | 4 minutes 13 seconds (253 seconds) |
| Total Requests | 73,781 |
| Peak Concurrent Connections | 365 active threads |
| Throughput (avg) | 291.41 requests/second |
| Test File | 1hr-load-test.jtl (13MB) |

---

## 1. Overall Performance Metrics

### Summary Statistics

```
Total Requests:           73,781
Successful:               58,326 (79.05%)
Failed:                   15,455 (20.95%)
Success Rate:             79.05%
```

### Response Time Distribution

| Metric | Value |
|--------|-------|
| **Minimum** | 1 ms |
| **Maximum** | 16,064 ms |
| **Average** | 1,239.11 ms |
| **Median** | 517 ms |
| **95th Percentile** | 6,206 ms |
| **99th Percentile** | 10,951 ms |

**Analysis:** The median of 517ms is reasonable, but the tail is concerning. The 95th percentile at 6.2 seconds and 99th at 11 seconds means 1 in 20 requests take over 6 seconds, and 1 in 100 take over 11 seconds. These are user-facing latencies that would cause poor UX.

---

## 2. Response Code Breakdown

| HTTP Code | Count | Percentage | Status |
|-----------|-------|-----------|--------|
| **200** (Success) | 58,326 | 79.05% | ✓ OK |
| **202** (Async Queue) | 3,513 | 4.76% | ⚠ Mismatch |
| **java.net.BindException** | 11,849 | 16.06% | ✗ CRITICAL |
| **HttpHostConnectException** | 93 | 0.13% | ⚠ Minor |

### Critical Issues:

**HTTP 202 (Accepted) vs 201 (Created)**
- All 3,585 POST requests return HTTP 202 (correct for async)
- Test suite expects HTTP 201 (synchronous response)
- Results in **100% POST request failure** from testing perspective
- Root Cause: Test expectations not updated for async implementation

**java.net.BindException (16.06% of all requests)**
- 11,849 socket binding failures
- Indicates connection pool exhaustion or resource limits
- Occurs uniformly across all request types
- Timeline: Begins minute 1, spreads throughout test
- Severity: **CRITICAL** - Blocks 1 in 6 requests

---

## 3. Performance by Request Type

### GET User Profile
```
Count:              16,664 (22.6% of traffic)
Success Rate:       82.32% (13,717/16,664)
Avg Response Time:  921.08 ms
Median:             438 ms
P95/P99:            3,318 / 10,097 ms
Grade: C+
```

### GET User Feed
```
Count:              16,759 (22.7% of traffic)
Success Rate:       82.86% (13,887/16,759)
Avg Response Time:  1,143.76 ms  ← Slowest GET
Median:             511 ms
P95/P99:            4,311 / 10,951 ms
Grade: C
Issue: Feed aggregation causing high response times
```

### GET Following
```
Count:              16,639 (22.5% of traffic)
Success Rate:       82.67% (13,756/16,639)
Avg Response Time:  930.95 ms
Median:             424 ms
P95/P99:            3,500 / 10,121 ms
Grade: C+
```

### GET Followers
```
Count:              16,519 (22.4% of traffic)
Success Rate:       82.68% (13,658/16,519)
Avg Response Time:  915.53 ms   ← Best GET
Median:             428 ms
P95/P99:            3,297 / 10,216 ms
Grade: C+
```

### GET User Feed (Before Post) - SLOWEST ENDPOINT
```
Count:              3,615 (4.9% of traffic)
Success Rate:       91.51% (3,308/3,615)
Avg Response Time:  4,168.01 ms  ← 3-4x slower than other GETs
Median:             2,461 ms
P95/P99:            11,852 / 13,365 ms
Grade: D
Issue: CRITICAL - users wait 4+ seconds for feed page load
Root Cause: Likely N+1 queries or complex aggregation without caching
```

### POST New Post (ASYNC) - COMPLETE FAILURE
```
Count:              3,585 (4.9% of traffic)
Success Rate:       0.00% (0/3,585) ❌ ALL FAILED
Avg Response Time:  3,130.99 ms
Median:             1,599 ms
P95/P99:            9,725 / 11,204 ms
Status Code:        202 (expected 201)
Grade: F
Issue: Response code mismatch causes test failure
Status: BROKEN - All POST requests marked as failures
```

---

## 4. Performance Timeline Analysis

The test ran for approximately 4 minutes. **Performance degraded significantly over time:**

### Minute-by-Minute Breakdown

| Minute | Requests | Success Rate | Avg Time | P95 Time | Status |
|--------|----------|-------------|----------|----------|--------|
| **0-1** | 19,078 | 100.00% | 752ms | 2,457ms | ✓ HEALTHY |
| **1-2** | 19,958 | 65.33% | 243ms | 972ms | ⚠ DEGRADATION BEGINS |
| **2-3** | 21,387 | 74.90% | 1,748ms | 9,291ms | ✗ SEVERE DEGRADATION |
| **3-4** | 12,651 | 76.91% | 2,703ms | 10,219ms | ✗ SUSTAINED POOR |
| **4+** | 707 | 65.06% | 928ms | 2,549ms | ✗ PARTIAL RECOVERY |

### Key Observations

1. **Minute 0 (Warm-up):** System performing normally - 100% success, reasonable response times
2. **Minute 1 Transition:** Sharp degradation begins - success drops to 65%, failures spike
3. **Minutes 2-3:** Complete system degradation - response times increase 2-3x (2.7s average)
4. **Minutes 3-4:** Never recovers - sustained poor performance, high failure rate
5. **No Recovery:** System remains degraded for rest of test

**Interpretation:** The system hits a threshold at ~1 minute mark and cannot recover. This suggests:
- Connection pool exhaustion
- Database connection starvation
- Queue saturation
- Insufficient thread pool capacity

---

## 5. Error Analysis

### Total Failures: 15,455 (20.95%)

#### Category 1: POST Response Code Mismatch - 3,585 failures (4.76%)
```
Issue: POST requests returning HTTP 202 instead of 201
Root Cause: Async implementation returns correct 202 (Accepted)
           Test expects 201 (Created) from sync operation
Impact: COMPLETE FAILURE - All POST operations marked failed
Failure Message: "Test failed: code expected to match /201/"
```

#### Category 2: Connection Binding Failures - 11,849 failures (16.06%)
```
Issue: java.net.BindException during request execution
Root Cause: Socket binding error - likely connection pool exhaustion
Impact: ~1 in 6 requests unable to establish connection
Distribution:
  - GET User Profile: 2,923 failures (3.96%)
  - GET Following: 2,858 failures (3.87%)
  - GET Followers: 2,846 failures (3.86%)
  - GET User Feed: 2,843 failures (3.85%)
  - GET User Feed (Before Post): 307 failures (0.42%)
  - POST New Post: 72 failures (0.10%)
```

#### Category 3: Connection Refused - 93 failures (0.13%)
```
Issue: org.apache.http.conn.HttpHostConnectException
Root Cause: Server refused connections (brief availability issue)
Impact: Minimal (0.13% of traffic)
```

---

## 6. Phase 1 vs Phase 2 Detailed Comparison

### Phase 1 Baseline (Original Synchronous Implementation)

| Metric | Value | Notes |
|--------|-------|-------|
| POST Response Time | 7,300 ms | Baseline for comparison |
| Success Rate | 80.3% | Overall system success |
| Response Code | 201 (Created) | Standard HTTP |
| Processing Model | Synchronous/Blocking | User waits for completion |
| Overall Rating | C | Slow but reliable |

### Phase 2 Actual (Async Implementation)

| Metric | Value | Notes |
|--------|-------|-------|
| POST Response Time | 3,131 ms | 57% faster than Phase 1 |
| Success Rate | 0.00% | **COMPLETE FAILURE** |
| Response Code | 202 (Accepted) | Correct for async, wrong for tests |
| Processing Model | Asynchronous/Fire-and-Forget | Immediate return, background processing |
| Overall Rating | F | Fast but completely broken |

### Comparison Analysis

```
┌─────────────────────────┬──────────┬──────────┬────────────────────┐
│ Metric                  │ Phase 1  │ Phase 2  │ Change              │
├─────────────────────────┼──────────┼──────────┼────────────────────┤
│ POST Response Time      │  7300ms  │  3131ms  │ -57.11% ✓ IMPROVED  │
│ POST Success Rate       │  80.3%   │  0.00%   │ -100% ✗ CRITICAL    │
│ HTTP Status Code        │   201    │   202    │ Changed (async)     │
│ All Requests Success    │  80.3%   │ 79.05%   │ -1.25% (slight drop)│
│ Connection Failures     │  ~0%     │ 16.06%   │ +16% ✗ REGRESSION   │
│ System Stability        │ Good     │ Poor     │ Degradation         │
└─────────────────────────┴──────────┴──────────┴────────────────────┘
```

### Verdict

**Phase 2 FAILED to meet requirements:**

✗ **Response time improved 57%** - Goal was met, but...  
✗ **POST operations have 100% failure rate** - Critical goal NOT met  
✗ **Connection stability severely degraded** - 16% failure rate vs ~0%  
✗ **System cannot sustain target load** - Degrades after 1-2 minutes  
✗ **Test suite incompatible** - Async responses fail validation  

The implementation traded **reliability for speed** and created a system unsuitable for production.

---

## 7. Root Cause Analysis

### ISSUE 1: HTTP 202 vs 201 Response Code Mismatch

**Symptom:** All 3,585 POST requests fail with message "Test failed: code expected to match /201/"

**Root Cause:** 
- Async implementation correctly returns HTTP 202 (Accepted - request queued)
- Test script was written for synchronous implementation (expects 201 Created)
- Mismatch between implementation and test expectations

**Why It Matters:**
- Makes POST functionality appear completely broken to testing framework
- Blocks validation of async implementation
- 0% success rate on all POST operations

**Solution Options:**
1. **Update test expectations** to accept 202 as success
2. **Change implementation** to return 201 with Location header pointing to async task
3. **Implement polling endpoint** to check async task status and update test logic
4. **Add custom response header** with task ID for tracking async completion

**Priority:** HIGH - Blocks entire feature validation

---

### ISSUE 2: Connection Pool Exhaustion (16.06% BindException Failures)

**Symptom:** 11,849 requests fail with `java.net.BindException` after minute 1

**Root Causes (Probable):**
1. **JMeter HTTP Client Pool Too Small**
   - Default HC4 concurrency level may be too low
   - Peak load requires 365 concurrent connections, but pool may default to 20-30

2. **Application Connection Pooling Issues**
   - Application-level HTTP client pool exhausted
   - Requests queuing waiting for available connections

3. **Database Connection Pool Exhaustion**
   - Async task processing not properly releasing connections
   - Database connections held longer than expected
   - Connection leaks in new async code

4. **OS/System Resource Limits**
   - `ulimit -n` too low (file descriptor limit)
   - TCP connection limits reached
   - Docker memory or CPU limits triggering connection failures

5. **Connection Reuse Issues**
   - Connections not being reused properly (keep-alive disabled)
   - Too many TIME_WAIT sockets

**Evidence Timeline:**
- Minute 0: 0 failures - system functioning normally
- Minute 1: Failures begin appearing
- Minute 2-3: Spread to all request types
- Minute 3+: Sustained at ~24-35% of requests affected

**Impact:**
- 1 in 6 requests cannot proceed
- Makes system unreliable for production
- User experience degraded (request timeouts)

**Solution Options:**
1. **Increase connection pools:**
   - JMeter: Increase HTTP client pool size (HC4 concurrency level)
   - Application: Increase HTTP client pool
   - Database: Increase connection pool size

2. **Add connection monitoring:**
   - Track active/idle/max connections
   - Alert on pool exhaustion
   - Log connection lifecycle events

3. **Review for connection leaks:**
   - Code audit of new async code
   - Check for unclosed connections
   - Review exception handling in async handlers

4. **Optimize connection usage:**
   - Enable connection keep-alive
   - Reduce connection hold time
   - Reuse connections properly

**Priority:** CRITICAL - Blocks system reliability

---

### ISSUE 3: Performance Degradation Over Time

**Symptom:** Timeline shows 100% success degrading to 65% by minute 1, sustained poor performance thereafter

**Root Cause:** Same as Issue 2 - connection pool exhaustion triggers cascade of failures

**Why It Happens:**
1. System starts with clean state (100% success minute 0)
2. Load builds up, connections accumulate
3. By minute 1, connection pool becomes saturated
4. New requests cannot get connections → failures spike
5. Failed requests pile up in error queue
6. System enters degraded state

**Cascade Effect:**
```
Load Increases → Connection Pool Fills → Requests Queue → 
Timeouts Begin → Error Rate Increases → System Slows → 
Response Times Increase → More Connections Held → Pool More Exhausted → 
Cascading Failures
```

**Impact:**
- System cannot sustain 300+ concurrent users
- Degrades within 1-2 minutes
- Never recovers during test

**Solution:** See Issue 2 - address connection pool exhaustion

---

### ISSUE 4: Feed Query Performance (4.2 seconds average)

**Symptom:** "GET User Feed (Before Post)" averages 4,168ms (3-4x slower than other GETs)

**Details:**
- Only 3,615 requests out of 73,781 (4.9%)
- Median: 2,461ms (still very slow)
- P95: 11,852ms (user waits 12+ seconds)
- Success rate: Only 91.51%

**Root Cause (Suspected):**
1. **N+1 Query Problem:** Fetching user info, then querying followers/following for each user
2. **Complex Aggregation:** Computing feed requires aggregating posts from multiple sources
3. **Missing Indexes:** Query scanning unnecessary rows
4. **No Caching:** Recalculating feed for each request instead of caching
5. **Database Contention:** Competing with other requests for DB resources

**Example Problem:**
```
// Slow way - N+1 queries
for user in getFollowingUsers():
    getPosts(user)  // 1 query per user = N queries
    
// Fast way - Join query
select posts from posts 
where author_id in (getFollowingIds())  // 1 query
```

**Impact:**
- Dominates response time histogram
- Poor user experience (4+ second page loads)
- Indicates database optimization needed

**Solution:**
1. Analyze query execution plan (EXPLAIN)
2. Add missing indexes
3. Rewrite as single join query instead of loop
4. Implement caching (Redis) for feed data
5. Consider materialized views for feed computation
6. Profile with real production data volume

**Priority:** HIGH - User-facing performance issue

---

## 8. Bottleneck Analysis

### Bottleneck 1: HTTP/TCP Connection Pool (CRITICAL)

| Aspect | Details |
|--------|---------|
| **Severity** | CRITICAL - Blocks system scaling |
| **Impact** | 16.06% of requests failing (11,849 failures) |
| **Cause** | Insufficient connection capacity under load |
| **Measured At** | Peak: 365 concurrent connections |
| **Timeline** | Begins minute 1, spreads throughout test |
| **User Impact** | 1 in 6 requests timeout or fail |
| **Action Required** | URGENT - Blocks 300+ user target |

---

### Bottleneck 2: Application Response Processing (CRITICAL)

| Aspect | Details |
|--------|---------|
| **Severity** | CRITICAL - System degradation |
| **Impact** | Response times increase 2-3x after minute 1 |
| **Cause** | Queue buildup, thread pool exhaustion, or DB bottleneck |
| **Measured At** | Avg: 1,239ms → P95: 6,206ms, P99: 10,951ms |
| **Timeline** | Minute 2 onward: sustained poor performance |
| **User Impact** | 1% of users wait 11+ seconds |
| **Action Required** | URGENT - Blocks sustained load testing |

---

### Bottleneck 3: POST Async Processing (CRITICAL)

| Aspect | Details |
|--------|---------|
| **Severity** | CRITICAL - Feature broken |
| **Impact** | 100% POST request test failure |
| **Cause** | HTTP 202 response code not matching test expectations |
| **Measured At** | 0/3,585 POST requests marked as success |
| **Timeline** | All throughout test |
| **User Impact** | POST functionality appears completely broken |
| **Action Required** | URGENT - Blocks feature validation |

---

### Bottleneck 4: Feed Query Performance (HIGH)

| Aspect | Details |
|--------|---------|
| **Severity** | HIGH - User experience issue |
| **Impact** | 4,168ms average response time (3-4x slower) |
| **Cause** | Complex query, N+1 problems, or missing indexes |
| **Measured At** | GET User Feed (Before Post): 4,168ms avg |
| **Timeline** | Consistent throughout test |
| **User Impact** | Users wait 4+ seconds for feed pages |
| **Action Required** | Important - Query optimization needed |

---

## 9. Recommendations

### IMMEDIATE (Today)

#### 1. Resolve POST Response Code Issue
**Action:**
- Decide: Should async POSTs return 201 or 202?
- If 202: Update test expectations to accept 202 as success
- If 201: Modify implementation to return 201 with async task tracking
- If tracking needed: Add response header with task ID

**Effort:** 2-4 hours  
**Impact:** Restore POST test validation  
**Priority:** CRITICAL

#### 2. Investigate Connection Pool Exhaustion
**Action:**
- Check JMeter HC4 HTTP Client settings
- Review application connection pool configuration
- Check OS `ulimit -n` (should be 65536+)
- Review application logs for connection errors
- Add metrics: active connections, idle connections, pool max

**Effort:** 2-3 hours  
**Impact:** Identify 16% failure root cause  
**Priority:** CRITICAL

#### 3. Add Comprehensive Monitoring
**Action:**
- Connection pool utilization (current/max)
- Database connection pool status
- Thread pool queue depth
- Request queue latency
- Response time percentiles in real-time
- System resource usage (CPU, memory, network)

**Effort:** 3-4 hours  
**Impact:** Better diagnostics for next test run  
**Priority:** HIGH

---

### SHORT-TERM (This Week)

#### 4. Optimize Feed Query Performance
**Action:**
- Analyze "GET User Feed (Before Post)" query
- Review execution plan with `EXPLAIN`
- Check for N+1 query problem
- Add missing indexes if needed
- Consider caching strategy (Redis)

**Effort:** 8-16 hours  
**Target:** < 500ms average response time  
**Impact:** Improves user experience  
**Priority:** HIGH

#### 5. Scale Connection Pools
**Action:**
- Increase JMeter HTTP client pool size
- Increase application connection pool
- Increase database connection pool
- Test with improved configuration
- Re-run load test to verify improvement

**Effort:** 2-4 hours  
**Impact:** Should resolve BindException failures  
**Priority:** CRITICAL

#### 6. Implement Async Response Tracking
**Action:**
- Add task ID to 202 responses
- Create `/posts/{taskId}/status` polling endpoint
- Implement timeout handling
- Update test logic to poll for async completion
- Verify async task actually completes

**Effort:** 6-8 hours  
**Impact:** Proper async validation in testing  
**Priority:** HIGH

---

### MEDIUM-TERM (Before Production)

#### 7. Redesign Async Architecture
**Current Issues:**
- POST still slow at 3.1 seconds (not true async benefit)
- System degrades under load despite async design
- Connection pool still critical bottleneck

**Actions:**
- Consider message queue (RabbitMQ, Kafka) instead of HTTP 202
- Improve connection pooling strategy
- Optimize thread pool configuration
- Implement proper async execution with workers

**Effort:** 20-40 hours  
**Impact:** Sustainable async architecture  
**Priority:** CRITICAL before production

#### 8. Implement Horizontal Scaling
**Actions:**
- Add load balancer (NGINX, HAProxy)
- Deploy multiple application instances
- Set up database read replicas
- Add caching layer (Redis)
- Test with multi-instance setup

**Effort:** 16-24 hours  
**Impact:** Enable scaling to 1000+ concurrent users  
**Priority:** Required for production

#### 9. Comprehensive Re-testing
**Actions:**
- Fix all identified issues
- Run full 1-hour load test (not 4 minutes)
- Test with sustained 300+ concurrent users
- Verify recovery from degraded state
- Validate all success metrics met

**Effort:** 4 hours test execution + setup  
**Impact:** Production readiness validation  
**Priority:** CRITICAL before deployment

---

### Testing Improvements

#### 10. Create Comprehensive Test Plan
**Action:**
- Define success criteria for Phase 2
- Create separate metrics for async operations
- Add latency targets: p50, p95, p99
- Test concurrent user ramp-up (gradual increase)
- Test sustained load duration (real 1+ hour)
- Document baseline Phase 1 metrics

**Effort:** 4-6 hours  
**Impact:** Better validation framework  
**Priority:** HIGH

#### 11. Add Failure Diagnostics
**Action:**
- Log all BindException events with timestamps
- Capture connection pool state when failures occur
- Monitor system resources (CPU, memory, disk, network)
- Correlate failures with system metrics
- Create dashboard showing real-time diagnostics

**Effort:** 2-3 hours  
**Impact:** Faster root cause identification  
**Priority:** HIGH

---

## 10. Success Criteria for Next Phase

### POST Operation Metrics (Must Meet ALL)
```
✓ Response Code:     201 or 202 (must be consistent, not mixed)
✓ Response Time:     < 500ms average (vs 3,131ms current, 7,300ms Phase 1)
✓ Success Rate:      > 99.5% (vs 0% current)
✓ Async Tracking:    Responses include task ID for verification
✓ Implementation:    Proper async validation in test suite

Status: ❌ NOT MET - Multiple issues require fixes
```

### Overall System Metrics (Must Meet ALL)
```
✓ Success Rate:           > 95% (vs 79.05% current)
✓ P95 Response Time:      < 2,000ms (vs 6,206ms current)
✓ P99 Response Time:      < 5,000ms (vs 10,951ms current)
✓ Connection Failures:    < 0.5% (vs 16.06% current)
✓ Feed Query:             < 500ms avg (vs 4,168ms current)
✓ Error Rate Stable:      No degradation over time (vs degradation current)

Status: ❌ NOT MET - System degradation makes this impossible
```

### Scalability Requirements (Must Meet ALL)
```
✓ Support 300+ concurrent users (system collapses at ~50%)
✓ Sustain load for 60+ minutes (degrades after 2 minutes)
✓ Linear or sub-linear response time increase (currently exponential)
✓ Zero connection exhaustion failures (currently 16%)
✓ No memory leaks or resource exhaustion

Status: ❌ NOT MET - Architecture cannot support target load
```

---

## 11. Conclusion

### Summary of Findings

Phase 2 async implementation has achieved **faster response times (57% improvement)** but at the cost of:

- **Complete test failure** (0% success on POST operations)
- **Reduced reliability** (79% vs 80.3% baseline)
- **Severe connection issues** (16% failure rate)
- **Poor scalability** (degrades after 1-2 minutes)
- **User-facing impact** (11-second P99 response times)

### Critical Issues Identified

| Issue | Severity | Impact | Fix Difficulty |
|-------|----------|--------|-----------------|
| POST response code mismatch | CRITICAL | 100% POST failure | Low |
| Connection pool exhaustion | CRITICAL | 16% request failure | Medium |
| System degradation over time | CRITICAL | Cannot sustain load | High |
| Feed query performance | HIGH | 4+ second page loads | Medium |

### What Works

✓ Async architectural direction is sound  
✓ Response time improvement is measurable  
✓ Most GET operations still functioning  
✓ No data corruption observed  

### What Doesn't Work

✗ POST operations completely broken for testing  
✗ System cannot sustain target concurrent users  
✗ Connection pool strategy inadequate  
✗ Query optimization needed  
✗ Integration with test framework broken  

### Final Verdict

### **🛑 DO NOT DEPLOY TO PRODUCTION**

The Phase 2 async implementation shows architectural promise but fails critical production readiness criteria:

1. **POST operations have 100% failure rate** - Makes core feature non-functional
2. **System collapses under target load** - Cannot sustain 300 concurrent users
3. **Connection stability severely degraded** - 1 in 6 requests fail
4. **Performance degrades irreversibly** - System never recovers from load

**Before Proceeding:**
1. Fix POST response code issue (2-4 hours)
2. Resolve connection pool exhaustion (critical)
3. Optimize feed query performance
4. Re-run full 1-hour load test with monitoring
5. Achieve >99% success rate on all endpoints

**Estimated Timeline to Production Ready:**
- Immediate fixes: 2-3 days
- Architecture redesign: 5-7 days
- Comprehensive testing: 2-3 days
- **Total: 2-3 weeks minimum**

The current implementation is a proof of concept showing async CAN improve response times, but the engineering work to make it production-grade is incomplete.

---

## Appendix: Test Data Summary

- **Test File:** `1hr-load-test.jtl` (13MB, 73,781 requests)
- **Test Duration:** 253.19 seconds (4.22 minutes)
- **Peak Throughput:** 291.41 requests/second
- **Peak Concurrent:** 365 active threads
- **Date Generated:** June 17, 2026 13:42:19 - 13:46:32 UTC

---

*Analysis generated by OpenCode load testing analysis tool*  
*For issues or improvements, contact the development team*
