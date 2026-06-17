#!/bin/bash

# Advanced High-Performance Load Test Script for Twitter Feed System
# Features:
#   - High concurrency (10-50 concurrent users)
#   - Better metrics collection (RPS, response times, percentiles)
#   - Realistic scenario testing
#   - Minimal artificial delays

# Color codes
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

# Default configuration
BASE_URL="http://localhost:8080/api/v1"
NUM_USERS=8  # Use existing test users (1-8)
POSTS_PER_USER=3
CONCURRENT_USERS=20
LOAD_TEST_REQUESTS=500
THINK_TIME=0.01

# Existing user IDs in database (from setup)
declare -a EXISTING_USERS=(1 2 3 4 5 6 7 8)

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -u|--users) NUM_USERS="$2"; shift 2 ;;
        -p|--posts) POSTS_PER_USER="$2"; shift 2 ;;
        -r|--requests) LOAD_TEST_REQUESTS="$2"; shift 2 ;;
        -c|--concurrent) CONCURRENT_USERS="$2"; shift 2 ;;
        -b|--base-url) BASE_URL="$2"; shift 2 ;;
        -t|--think-time) THINK_TIME="$2"; shift 2 ;;
        -h|--help)
            echo "Advanced Load Test for Twitter Feed System"
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  -u, --users NUM          Number of users (default: 8 - existing users)"
            echo "  -p, --posts NUM          Posts per user (default: 3)"
            echo "  -r, --requests NUM       Total load test requests (default: 500)"
            echo "  -c, --concurrent NUM     Concurrent workers (default: 20)"
            echo "  -t, --think-time SEC     Think time between requests (default: 0.01)"
            echo "  -h, --help               Show help"
            exit 0
            ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

# Cleanup function
cleanup() {
    echo -e "\n${YELLOW}🛑 Stopping load test...${NC}"
    pkill -P $$ 2>/dev/null
    exit 0
}
trap cleanup SIGINT SIGTERM

# Print banner
print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  Advanced Parallel Load Test${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_config() {
    echo -e "${CYAN}Configuration:${NC}"
    echo -e "  📊 Total users: ${YELLOW}$NUM_USERS${NC}"
    echo -e "  📝 Posts per user: ${YELLOW}$POSTS_PER_USER${NC}"
    echo -e "  👥 Concurrent workers: ${YELLOW}$CONCURRENT_USERS${NC}"
    echo -e "  🔄 Total requests: ${YELLOW}$LOAD_TEST_REQUESTS${NC}"
    echo -e "  ⏱️  Think time: ${YELLOW}${THINK_TIME}s${NC}"
    echo -e "  🌐 API: ${YELLOW}$BASE_URL${NC}\n"
}

# Check API health
check_api_health() {
    echo -e "${CYAN}Checking API health...${NC}"
    response=$(curl -s -w "%{http_code}" "http://localhost:8080/actuator/health" -o /dev/null 2>/dev/null)
    if [ "$response" = "200" ]; then
        echo -e "${GREEN}✓ API is healthy${NC}\n"
        return 0
    else
        echo -e "${RED}✗ API unhealthy (HTTP $response)${NC}"
        return 1
    fi
}

# Generate post content
generate_post_content() {
    local user_id=$1
    local post_num=$2
    local topics=("technology" "startup" "coding" "innovation" "AI" "development")
    local topic=${topics[$((RANDOM % ${#topics[@]}))]}
    echo "Load test post #$post_num from user $user_id about $topic. Testing parallel performance!"
}

# Create test posts in parallel
create_test_posts() {
    echo -e "${CYAN}Creating test posts in parallel...${NC}"
    
    local total_posts=$((${#EXISTING_USERS[@]} * POSTS_PER_USER))
    local post_count=0
    
    for user_id in "${EXISTING_USERS[@]}"; do
        for post_num in $(seq 1 $POSTS_PER_USER); do
            (
                local content=$(generate_post_content $user_id $post_num)
                curl -s -X POST "$BASE_URL/posts" \
                    -H "Content-Type: application/json" \
                    -d "{
                        \"userId\": $user_id,
                        \"content\": \"$content\",
                        \"mediaUrls\": [],
                        \"hashtags\": [\"load\", \"test\"]
                    }" 2>/dev/null > /dev/null
            ) &
            
            ((post_count++))
            # Limit background jobs
            if [ $((post_count % 30)) -eq 0 ]; then
                wait
            fi
        done
    done
    wait
    
    echo -e "${GREEN}✓ Created $total_posts posts${NC}\n"
}

# Perform single load test request
perform_request() {
    local request_num=$1
    # Pick random user from EXISTING_USERS array instead of 1..NUM_USERS
    local user_index=$((RANDOM % ${#EXISTING_USERS[@]}))
    local user_id=${EXISTING_USERS[$user_index]}
    local operation=$((RANDOM % 100))
    
    local start_time=$(date +%s%3N)
    local response_code
    local operation_name
    
    if [ $operation -lt 40 ]; then
        response_code=$(curl -s -w "%{http_code}" -o /dev/null "$BASE_URL/feed?userId=$user_id&limit=10&offset=0" 2>/dev/null)
        operation_name="GET_FEED"
    elif [ $operation -lt 70 ]; then
        response_code=$(curl -s -w "%{http_code}" -o /dev/null "$BASE_URL/users/$user_id" 2>/dev/null)
        operation_name="GET_USER"
    elif [ $operation -lt 85 ]; then
        local content=$(generate_post_content $user_id $request_num)
        response_code=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/posts" \
            -H "Content-Type: application/json" \
            -d "{
                \"userId\": $user_id,
                \"content\": \"$content - Request $request_num\",
                \"mediaUrls\": [],
                \"hashtags\": [\"load\", \"test\"]
            }" -o /dev/null 2>/dev/null)
        operation_name="CREATE_POST"
    elif [ $operation -lt 93 ]; then
        response_code=$(curl -s -w "%{http_code}" -o /dev/null "$BASE_URL/users/$user_id/followers" 2>/dev/null)
        operation_name="GET_FOLLOWERS"
    else
        response_code=$(curl -s -w "%{http_code}" -o /dev/null "$BASE_URL/users/$user_id/following" 2>/dev/null)
        operation_name="GET_FOLLOWING"
    fi
    
    local end_time=$(date +%s%3N)
    local response_time=$((end_time - start_time))
    
    # Log results
    echo "$operation_name|$response_code|$response_time" >> /tmp/load_test_results.log
    
    # Minimal think time
    sleep $THINK_TIME 2>/dev/null || true
}

# Worker function for concurrent requests
worker() {
    local worker_id=$1
    local requests_per_worker=$2
    local start_req=$3
    
    for req in $(seq 1 $requests_per_worker); do
        perform_request $((start_req + req))
    done
}

# Execute load test
execute_load_test() {
    echo -e "${CYAN}Executing load test with $CONCURRENT_USERS concurrent workers...${NC}"
    echo -e "${CYAN}Sending $LOAD_TEST_REQUESTS requests...${NC}\n"
    
    > /tmp/load_test_results.log
    
    local start_time=$(date +%s)
    local requests_per_worker=$((LOAD_TEST_REQUESTS / CONCURRENT_USERS))
    local remaining=$((LOAD_TEST_REQUESTS % CONCURRENT_USERS))
    
    # Launch workers
    for worker_id in $(seq 1 $CONCURRENT_USERS); do
        local reqs=$requests_per_worker
        if [ $worker_id -le $remaining ]; then
            reqs=$((reqs + 1))
        fi
        
        worker $worker_id $reqs $((worker_id * requests_per_worker)) &
    done
    
    # Wait for all workers
    wait
    
    local end_time=$(date +%s)
    local total_duration=$((end_time - start_time))
    
    echo -e "${GREEN}✓ Load test complete in ${YELLOW}${total_duration}s${NC}\n"
    echo "$total_duration" > /tmp/test_duration.txt
}

# Report results
report_results() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  Load Test Results${NC}"
    echo -e "${BLUE}========================================${NC}\n"
    
    if [ ! -f /tmp/load_test_results.log ] || [ ! -s /tmp/load_test_results.log ]; then
        echo -e "${RED}No results found${NC}"
        return 1
    fi
    
    local total_requests=$(wc -l < /tmp/load_test_results.log)
    local successful=$(grep -c '|20[0-9]|' /tmp/load_test_results.log 2>/dev/null || echo 0)
    local failed=$((total_requests - successful))
    
    local success_rate=$((successful * 100 / total_requests))
    
    echo -e "${YELLOW}📊 Overall Results:${NC}"
    echo -e "  ${CYAN}Total Requests:${NC} ${YELLOW}$total_requests${NC}"
    echo -e "  ${CYAN}Successful (2xx):${NC} ${GREEN}$successful${NC}"
    echo -e "  ${CYAN}Failed:${NC} ${RED}$failed${NC}"
    echo -e "  ${CYAN}Success Rate:${NC} ${YELLOW}${success_rate}%${NC}"
    
    # RPS calculation
    if [ -f /tmp/test_duration.txt ]; then
        local duration=$(cat /tmp/test_duration.txt)
        if [ "$duration" -gt 0 ]; then
            local rps=$(echo "scale=2; $total_requests / $duration" | bc 2>/dev/null || echo "N/A")
            echo -e "  ${CYAN}Throughput (RPS):${NC} ${YELLOW}${rps}${NC}"
        fi
    fi
    
    # Response times
    echo -e "\n${YELLOW}⏱️  Response Time Statistics:${NC}"
    
    if command -v awk &> /dev/null; then
        local min=$(awk -F'|' '{print $3}' /tmp/load_test_results.log 2>/dev/null | sort -n | head -1)
        local max=$(awk -F'|' '{print $3}' /tmp/load_test_results.log 2>/dev/null | sort -n | tail -1)
        local avg=$(awk -F'|' '{sum+=$3; count++} END {if (count>0) printf "%.1f", sum/count}' /tmp/load_test_results.log 2>/dev/null || echo "N/A")
        
        echo -e "  ${CYAN}Min:${NC} ${YELLOW}${min}ms${NC}"
        echo -e "  ${CYAN}Avg:${NC} ${YELLOW}${avg}ms${NC}"
        echo -e "  ${CYAN}Max:${NC} ${YELLOW}${max}ms${NC}"
        
        # Percentiles
        if [ "$total_requests" -gt 100 ]; then
            local p50=$(awk -F'|' '{print $3}' /tmp/load_test_results.log | sort -n | awk "NR == int((NR-1)*0.50)+1 {print; exit}")
            local p95=$(awk -F'|' '{print $3}' /tmp/load_test_results.log | sort -n | awk "NR == int((NR-1)*0.95)+1 {print; exit}")
            local p99=$(awk -F'|' '{print $3}' /tmp/load_test_results.log | sort -n | awk "NR == int((NR-1)*0.99)+1 {print; exit}")
            
            echo -e "  ${CYAN}P50:${NC} ${YELLOW}${p50}ms${NC}"
            echo -e "  ${CYAN}P95:${NC} ${YELLOW}${p95}ms${NC}"
            echo -e "  ${CYAN}P99:${NC} ${YELLOW}${p99}ms${NC}"
        fi
    fi
    
    # Operation breakdown
    echo -e "\n${YELLOW}📈 Operation Breakdown:${NC}"
    awk -F'|' '{print $1}' /tmp/load_test_results.log 2>/dev/null | sort | uniq -c | while read count op; do
        local op_success=$(grep "^${op}|20[0-9]" /tmp/load_test_results.log 2>/dev/null | wc -l)
        local op_rate=$((op_success * 100 / count))
        echo -e "  ${CYAN}$op:${NC} ${YELLOW}$count${NC} (${GREEN}${op_rate}%${NC})"
    done
    
    # HTTP Status
    echo -e "\n${YELLOW}🔗 HTTP Status Codes:${NC}"
    awk -F'|' '{print $2}' /tmp/load_test_results.log 2>/dev/null | sort | uniq -c | while read count code; do
        if [[ $code =~ ^2 ]]; then
            echo -e "  ${GREEN}HTTP $code:${NC} ${YELLOW}$count${NC}"
        else
            echo -e "  ${RED}HTTP $code:${NC} ${YELLOW}$count${NC}"
        fi
    done
}

# Main execution
main() {
    print_header
    print_config
    
    # Check dependencies
    echo -e "${CYAN}Checking dependencies...${NC}"
    if ! command -v curl &> /dev/null; then
        echo -e "${RED}✗ curl is required${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ Dependencies OK${NC}\n"
    
    # Check API
    if ! check_api_health; then
        exit 1
    fi
    
    # Create test posts
    create_test_posts
    
    # Warmup
    echo -e "${CYAN}Warming up system (10 requests)...${NC}"
    for i in $(seq 1 10); do
        perform_request $i &
    done
    wait
    echo -e "${GREEN}✓ Warmup complete${NC}\n"
    
    # Run load test
    execute_load_test
    
    # Report
    report_results
    
    # Cleanup
    rm -f /tmp/load_test_results.log /tmp/test_duration.txt
    
    echo -e "\n${CYAN}🔗 Monitoring:${NC}"
    echo -e "  Application: http://localhost:8080"
    echo -e "  Grafana: http://localhost:3000"
    echo -e "  Prometheus: http://localhost:9090\n"
}

main
