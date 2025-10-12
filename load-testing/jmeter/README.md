# JMeter Load Testing for Twitter Feed System

This directory contains a comprehensive JMeter test plan for load testing the Twitter Feed System with its hybrid architecture (PostgreSQL + Cassandra + Redis).

## 📋 Test Plan Overview

### Test Structure
The test plan simulates realistic user behavior with three distinct user groups:

1. **Browse Users** (30 users, 60s ramp-up, 10 loops)
   - Random browsing behavior
   - Get user profiles, feeds, followers, following
   - Gaussian random thinking time (2±1 seconds)

2. **Active Users - Content Creators** (15 users, 30s ramp-up, 5 loops)
   - Check their feed first
   - Create new posts with realistic content
   - Uniform random posting delays (3±2 seconds)

3. **Celebrity Feed Consumers** (20 users, 45s ramp-up, 15 loops)
   - Frequently check celebrity feeds
   - Simulates users following popular accounts
   - Constant 1.5s intervals between checks

### Total Load
- **65 concurrent users** at peak
- **~1,200+ requests** total
- **Mixed read/write operations** (80% reads, 20% writes)
- **Realistic timing patterns**

## 🚀 Quick Start

### Prerequisites
1. **Java 8+** installed
2. **Apache JMeter** downloaded and installed
   - Download from: https://jmeter.apache.org/download_jmeter.cgi
   - Extract to desired location

3. **Twitter Feed System** running
   - Application: http://localhost:8080
   - Database containers running

### Running the Test

#### Option 1: GUI Mode (Recommended for Development)
```bash
# Navigate to JMeter installation directory
cd /path/to/apache-jmeter-x.x

# Start JMeter GUI
./bin/jmeter

# In JMeter GUI:
# 1. File → Open → Select twitter-feed-load-test.jmx
# 2. Click the green "Start" button
# 3. View real-time results in listeners
```

#### Option 2: Command Line Mode (Recommended for CI/CD)
```bash
# Navigate to the load testing directory
cd "d:/java projects/hybrid_data_system/load-testing/jmeter"

# Run test in non-GUI mode
jmeter -n -t twitter-feed-load-test.jmx -l results/test-results.jtl -e -o results/html-report

# Generate HTML report from existing results
jmeter -g results/test-results.jtl -o results/html-report
```

## 📊 Monitoring & Results

### Real-time Monitoring
While the test runs, monitor:
- **Application logs**: Watch Spring Boot console
- **Grafana Dashboard**: http://localhost:3000/d/twitter-feed-metrics/application-metrics
- **JMeter Listeners**: View Results Tree, Summary Report, Aggregate Report

### Key Metrics to Watch
- **Response Times**: Should be < 500ms for 95th percentile
- **Throughput**: Requests per second
- **Error Rate**: Should be < 1%
- **Resource Utilization**: CPU, Memory, Database connections

### JMeter Listeners Included
1. **View Results Tree**: Individual request/response details
2. **Summary Report**: Basic statistics overview
3. **Aggregate Report**: Detailed performance metrics
4. **Response Times Over Time**: Performance trends

## 🎯 Test Data

### CSV Data Files
- `data/user_ids.csv`: User IDs 1-8 for testing
- `data/celebrity_ids.csv`: Celebrity user IDs 6,7,8
- `data/post_content.csv`: 20 realistic post samples

### API Endpoints Tested
- `GET /api/v1/users/{id}` - User profiles
- `GET /api/v1/feeds/{id}?page=X&size=Y` - User feeds
- `GET /api/v1/users/{id}/followers` - Followers list
- `GET /api/v1/users/{id}/following` - Following list
- `POST /api/v1/posts` - Create new posts

## ⚙️ Customization

### Adjusting Load Parameters
Edit the test plan to modify:

```xml
<!-- Thread Group Settings -->
<stringProp name="ThreadGroup.num_threads">30</stringProp>  <!-- Number of users -->
<stringProp name="ThreadGroup.ramp_time">60</stringProp>    <!-- Ramp-up time in seconds -->
<stringProp name="LoopController.loops">10</stringProp>     <!-- Number of iterations -->
```

### Environment Configuration
Update variables at the test plan level:
- `BASE_URL`: Server hostname (default: localhost)
- `PORT`: Server port (default: 8080)
- `API_PATH`: API base path (default: /api/v1)

### Adding New Test Scenarios
1. Create new Thread Group
2. Add HTTP Request samplers
3. Configure appropriate timers
4. Add response assertions
5. Update CSV data files as needed

## 📈 Performance Baselines

### Expected Performance (Local Development)
- **Average Response Time**: 50-200ms
- **95th Percentile**: < 500ms
- **Throughput**: 100-300 requests/sec
- **Error Rate**: < 0.1%

### Hybrid Architecture Benefits
- **PostgreSQL**: User data, relationships (ACID compliance)
- **Cassandra**: Posts, feeds (High write throughput)
- **Redis**: Caching, sessions (Sub-millisecond reads)

## 🔧 Troubleshooting

### Common Issues
1. **Connection Refused**
   - Ensure application is running on localhost:8080
   - Check if containers are up: `docker ps`

2. **High Error Rates**
   - Reduce concurrent users
   - Increase ramp-up time
   - Check application logs for errors

3. **Slow Response Times**
   - Monitor database connections
   - Check resource utilization
   - Review Grafana metrics

### Debug Mode
Run with verbose logging:
```bash
jmeter -n -t twitter-feed-load-test.jmx -l results/debug.jtl -JjmeterengCHeckResultheckd=true
```

## 📋 Results Analysis

### Key Reports
1. **HTML Dashboard**: Professional report with charts
2. **JTL Files**: Raw results for custom analysis
3. **Grafana Metrics**: Application-level monitoring
4. **JMeter Logs**: Detailed execution information

### Performance Tuning
Based on results, consider:
- Database connection pool sizing
- Redis cache hit rates
- Cassandra write patterns
- Application thread pool configuration

## 🚀 CI/CD Integration

### Jenkins Pipeline Example
```groovy
stage('Performance Test') {
    steps {
        sh 'jmeter -n -t load-testing/jmeter/twitter-feed-load-test.jmx -l results/perf-test.jtl'
        perfReport 'results/perf-test.jtl'
    }
}
```

### Performance Gates
Set thresholds for:
- Average response time < 200ms
- 95th percentile < 500ms
- Error rate < 1%
- Throughput > 100 RPS

---

## 🎯 Next Steps

1. **Baseline Testing**: Run tests to establish performance baselines
2. **Stress Testing**: Gradually increase load to find breaking points
3. **Endurance Testing**: Long-running tests to detect memory leaks
4. **Spike Testing**: Sudden load increases to test auto-scaling

For questions or issues, refer to the main project documentation or application logs.