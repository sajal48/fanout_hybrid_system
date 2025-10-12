#!/bin/bash

# Twitter Feed System Load Test Script
# Usage: ./test-load.sh [OPTIONS]
# 
# Options:
#   -u, --users NUM          Number of users to create (default: 10)
#   -p, --posts NUM          Posts per user (default: 5)
#   -i, --iterations NUM     Load test iterations (default: 100)
#   -b, --base-url URL       Base API URL (default: http://localhost:8080/api/v1)
#   -h, --help               Show this help message

# Color codes for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Default configuration
BASE_URL="http://localhost:8080/api/v1"
NUM_USERS=10
POSTS_PER_USER=5
LOAD_TEST_ITERATIONS=100
CONCURRENT_REQUESTS=3

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -u|--users)
            NUM_USERS="$2"
            shift 2
            ;;
        -p|--posts)
            POSTS_PER_USER="$2"
            shift 2
            ;;
        -i|--iterations)
            LOAD_TEST_ITERATIONS="$2"
            shift 2
            ;;
        -c|--concurrent)
            CONCURRENT_REQUESTS="$2"
            shift 2
            ;;
        -b|--base-url)
            BASE_URL="$2"
            shift 2
            ;;
        -h|--help)
            echo "Twitter Feed System Load Test Script"
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -u, --users NUM          Number of users to create (default: 10)"
            echo "  -p, --posts NUM          Posts per user (default: 5)"
            echo "  -i, --iterations NUM     Load test iterations (default: 100)"
            echo "  -c, --concurrent NUM     Concurrent users (default: 3)"
            echo "  -b, --base-url URL       Base API URL (default: http://localhost:8080/api/v1)"
            echo "  -h, --help               Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                                    # Run with defaults"
            echo "  $0 -u 20 -p 10 -i 200               # 20 users, 10 posts each, 200 iterations"
            echo "  $0 --base-url http://prod.api.com/v1 # Test against different environment"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use -h or --help for usage information"
            exit 1
            ;;
    esac
done

# Validate numeric parameters
if ! [[ "$NUM_USERS" =~ ^[0-9]+$ ]] || [ "$NUM_USERS" -lt 1 ]; then
    echo -e "${RED}Error: Number of users must be a positive integer${NC}"
    exit 1
fi

if ! [[ "$POSTS_PER_USER" =~ ^[0-9]+$ ]] || [ "$POSTS_PER_USER" -lt 1 ]; then
    echo -e "${RED}Error: Posts per user must be a positive integer${NC}"
    exit 1
fi

if ! [[ "$LOAD_TEST_ITERATIONS" =~ ^[0-9]+$ ]] || [ "$LOAD_TEST_ITERATIONS" -lt 1 ]; then
    echo -e "${RED}Error: Load test iterations must be a positive integer${NC}"
    exit 1
fi

# Function to print status
print_status() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓${NC} $2"
    else
        echo -e "${RED}✗${NC} $2"
        return 1
    fi
}

# Function to check if API is available
check_api_health() {
    echo -e "${CYAN}Checking API health...${NC}"
    response=$(curl -s -w "%{http_code}" "http://localhost:8080/actuator/health" -o /dev/null)
    if [ "$response" = "200" ]; then
        print_status 0 "API is healthy and ready"
        return 0
    else
        echo -e "${RED}API health check failed (HTTP $response)${NC}"
        echo -e "${RED}Make sure the application is running on http://localhost:8080${NC}"
        return 1
    fi
}

# Function to generate realistic post content
generate_post_content() {
    local user_id=$1
    local post_num=$2
    
    local topics=("technology" "startup" "coding" "innovation" "AI" "development" "design" "business")
    local hashtags=("#tech" "#startup" "#code" "#innovation" "#AI" "#dev" "#design" "#business" "#programming" "#software")
    local emojis=("🚀" "💡" "🔥" "⚡" "🌟" "💻" "🎯" "🎉")
    
    local topic=${topics[$((RANDOM % ${#topics[@]}))]}
    local hashtag=${hashtags[$((RANDOM % ${#hashtags[@]}))]}
    local emoji=${emojis[$((RANDOM % ${#emojis[@]}))]}
    
    echo "Exploring the world of $topic today! Post #$post_num from user $user_id $emoji $hashtag #test"
}

# Cleanup function for graceful shutdown
cleanup() {
    echo -e "\n${YELLOW}🛑 Received interrupt signal. Cleaning up...${NC}"
    echo -e "${CYAN}Load test stopped gracefully.${NC}"
    exit 0
}

# Set up signal handlers
trap cleanup SIGINT SIGTERM

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Twitter Feed System - Load Test${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "${CYAN}Configuration:${NC}"
echo -e "  📊 Users to create: ${YELLOW}$NUM_USERS${NC}"
echo -e "  📝 Posts per user: ${YELLOW}$POSTS_PER_USER${NC}"
echo -e "  🔄 Load test iterations: ${YELLOW}$LOAD_TEST_ITERATIONS${NC}"
echo -e "  👥 Concurrent users: ${YELLOW}$CONCURRENT_REQUESTS${NC}"
echo -e "  🌐 API Base URL: ${YELLOW}$BASE_URL${NC}"
echo -e "${BLUE}========================================${NC}\n"

# Check dependencies
echo -e "${CYAN}Checking dependencies...${NC}"
if ! command -v curl &> /dev/null; then
    echo -e "${RED}Error: curl is required but not installed${NC}"
    exit 1
fi

if ! command -v bc &> /dev/null; then
    echo -e "${YELLOW}Warning: bc not found. Performance calculations may be limited${NC}"
fi

print_status 0 "All dependencies available"

# Store existing user IDs and created post IDs
declare -a USER_IDS=(1 2 3 4 5 6 7 8)  # Use existing test users
declare -a POST_IDS
declare -a CELEBRITY_IDS=(6 7 8)  # Users 6, 7, 8 are celebrities

# Track statistics
SUCCESS_COUNT=0
FAILED_COUNT=0
START_TIME=$(date +%s)

# Check API health before starting
echo -e "${CYAN}Checking API health...${NC}"
if ! check_api_health; then
    exit 1
fi

echo -e "\n${YELLOW}Using existing test users (IDs: ${USER_IDS[@]})...${NC}"
echo -e "${CYAN}Celebrity users: ${CELEBRITY_IDS[@]}${NC}"

# Verify users exist
echo -e "\n${YELLOW}Verifying users exist...${NC}"
for user_id in "${USER_IDS[@]}"; do
    response=$(curl -s -w "%{http_code}" "$BASE_URL/users/$user_id" -o /dev/null)
    if [ "$response" = "200" ]; then
        print_status 0 "User $user_id exists"
        ((SUCCESS_COUNT++))
    else
        print_status 1 "User $user_id not found (HTTP $response)"
        ((FAILED_COUNT++))
    fi
done

echo -e "\n${YELLOW}Creating new posts...${NC}"
# Create posts for each user
for user_id in "${USER_IDS[@]}"; do
    for post_num in $(seq 1 $POSTS_PER_USER); do
        content=$(generate_post_content $user_id $post_num)
        
        RESPONSE=$(curl -s -X POST "$BASE_URL/posts" \
            -H "Content-Type: application/json" \
            -d "{
                \"userId\": $user_id,
                \"content\": \"$content\",
                \"mediaUrls\": [],
                \"hashtags\": [\"test\", \"loadtest\"]
            }")

        POST_ID=$(echo $RESPONSE | grep -o '"postId":"[^"]*"' | cut -d'"' -f4)
        if [ ! -z "$POST_ID" ]; then
            POST_IDS+=($POST_ID)
            print_status 0 "Created post for user $user_id (Post ID: $POST_ID)"
            ((SUCCESS_COUNT++))
        else
            print_status 1 "Failed to create post for user $user_id"
            ((FAILED_COUNT++))
        fi

        # Small delay to avoid overwhelming the system
        sleep 0.1
    done
done

echo -e "\n${YELLOW}Testing feed retrieval...${NC}"
# Get feeds for each user to warm up the cache
for user_id in "${USER_IDS[@]}"; do
    response=$(curl -s -w "%{http_code}" "$BASE_URL/feed?userId=$user_id&limit=10&offset=0" -o /dev/null)
    if [ "$response" = "200" ]; then
        print_status 0 "Retrieved feed for user $user_id"
        ((SUCCESS_COUNT++))
    else
        print_status 1 "Failed to retrieve feed for user $user_id (HTTP $response)"
        ((FAILED_COUNT++))
    fi
    sleep 0.1
done

echo -e "\n${YELLOW}Generating continuous load...${NC}"
echo -e "${BLUE}Running $LOAD_TEST_ITERATIONS requests with $CONCURRENT_REQUESTS concurrent users...${NC}\n"

# Function to perform a single load test operation
perform_load_operation() {
    local user_iteration=$1
    local total_iterations=$2
    
    # Random user ID
    random_user_index=$((RANDOM % ${#USER_IDS[@]}))
    random_user_id=${USER_IDS[$random_user_index]}

    # Random operation with weighted probabilities
    operation_choice=$((RANDOM % 100))
    
    # Start timing for this operation
    op_start_time=$(date +%s%3N)

    if [ $operation_choice -lt 35 ]; then
        # 35% - Get user feed (most common operation)
        offset=$((RANDOM % 10))
        limit=$((RANDOM % 15 + 5))
        response=$(curl -s -w "%{http_code}" "$BASE_URL/feed?userId=$random_user_id&limit=$limit&offset=$offset" -o /dev/null 2>/dev/null)
        operation="GET_FEED"
        echo -e "${GREEN}[User$user_iteration-$total_iterations]${NC} GET user feed (ID: $random_user_id, limit: $limit, offset: $offset) - HTTP $response"
    elif [ $operation_choice -lt 55 ]; then
        # 20% - Get user profile
        response=$(curl -s -w "%{http_code}" "$BASE_URL/users/$random_user_id" -o /dev/null 2>/dev/null)
        operation="GET_USER"
        echo -e "${GREEN}[User$user_iteration-$total_iterations]${NC} GET user profile (ID: $random_user_id) - HTTP $response"
    elif [ $operation_choice -lt 70 ]; then
        # 15% - Create a new post
        content=$(generate_post_content $random_user_id $total_iterations)
        response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/posts" \
            -H "Content-Type: application/json" \
            -d "{
                \"userId\": $random_user_id,
                \"content\": \"$content - Parallel load test iteration $total_iterations\",
                \"mediaUrls\": [],
                \"hashtags\": [\"paralleltest\", \"user$user_iteration\", \"iter$total_iterations\"]
            }" -o /dev/null 2>/dev/null)
        operation="CREATE_POST"
        echo -e "${GREEN}[User$user_iteration-$total_iterations]${NC} POST new post (User ID: $random_user_id) - HTTP $response"
    elif [ $operation_choice -lt 85 ]; then
        # 15% - Get followers
        response=$(curl -s -w "%{http_code}" "$BASE_URL/users/$random_user_id/followers" -o /dev/null 2>/dev/null)
        operation="GET_FOLLOWERS"
        echo -e "${GREEN}[User$user_iteration-$total_iterations]${NC} GET followers (ID: $random_user_id) - HTTP $response"
    elif [ $operation_choice -lt 95 ]; then
        # 10% - Get following
        response=$(curl -s -w "%{http_code}" "$BASE_URL/users/$random_user_id/following" -o /dev/null 2>/dev/null)
        operation="GET_FOLLOWING"
        echo -e "${GREEN}[User$user_iteration-$total_iterations]${NC} GET following (ID: $random_user_id) - HTTP $response"
    else
        # 5% - Test celebrity feed (if available)
        if [ ${#CELEBRITY_IDS[@]} -gt 0 ]; then
            celebrity_index=$((RANDOM % ${#CELEBRITY_IDS[@]}))
            celebrity_id=${CELEBRITY_IDS[$celebrity_index]}
            response=$(curl -s -w "%{http_code}" "$BASE_URL/feed?userId=$celebrity_id&limit=10&offset=0" -o /dev/null 2>/dev/null)
            operation="GET_CELEBRITY_FEED"
            echo -e "${CYAN}[User$user_iteration-$total_iterations]${NC} GET celebrity feed (ID: $celebrity_id) - HTTP $response"
        else
            # Fallback to regular feed
            response=$(curl -s -w "%{http_code}" "$BASE_URL/feed?userId=$random_user_id&limit=10&offset=0" -o /dev/null 2>/dev/null)
            operation="GET_FEED"
            echo -e "${GREEN}[User$user_iteration-$total_iterations]${NC} GET user feed (ID: $random_user_id) - HTTP $response"
        fi
    fi

    # Record operation statistics (simplified for parallel execution)
    op_end_time=$(date +%s%3N)
    op_duration=$((op_end_time - op_start_time))
    
    # Write stats to temporary files (will be aggregated later)
    echo "$operation" >> /tmp/operations_$user_iteration.log
    echo "$op_duration" >> /tmp/durations_$user_iteration.log
    
    # Track response codes
    if [[ "$response" =~ ^[0-9]+$ ]]; then
        if [[ $response -ge 200 && $response -lt 300 ]]; then
            echo "SUCCESS" >> /tmp/results_$user_iteration.log
        else
            echo "FAILED" >> /tmp/results_$user_iteration.log
            echo -e "${RED}Error: HTTP $response for $operation${NC}"
        fi
    else
        echo "FAILED" >> /tmp/results_$user_iteration.log
        echo -e "${RED}Invalid response: $response for $operation${NC}"
    fi

    # Random delay between requests
    sleep_duration=$(echo "0.01 + ($RANDOM % 50) / 1000" | awk '{print $1 + $2}')
    sleep "$sleep_duration" 2>/dev/null || sleep 0.03
}

# Function to simulate a concurrent user
simulate_user() {
    local user_id=$1
    local iterations_per_user=$2
    
    for iteration in $(seq 1 $iterations_per_user); do
        perform_load_operation $user_id $iteration
    done
}

# Performance tracking
LOAD_START_TIME=$(date +%s)
declare -A OPERATION_COUNTS
declare -A OPERATION_TIMES

# Calculate iterations per user
iterations_per_user=$((LOAD_TEST_ITERATIONS / CONCURRENT_REQUESTS))
remaining_iterations=$((LOAD_TEST_ITERATIONS % CONCURRENT_REQUESTS))

# Start concurrent users
for user in $(seq 1 $CONCURRENT_REQUESTS); do
    # Give the first few users one extra iteration if there's a remainder
    user_iterations=$iterations_per_user
    if [ $user -le $remaining_iterations ]; then
        user_iterations=$((iterations_per_user + 1))
    fi
    
    simulate_user $user $user_iterations &
done

# Wait for all background processes to complete
echo -e "${YELLOW}Waiting for all concurrent users to complete...${NC}"
wait

# Aggregate statistics from temporary files
declare -A OPERATION_COUNTS
declare -A OPERATION_TIMES
SUCCESS_COUNT=0
FAILED_COUNT=0

for user in $(seq 1 $CONCURRENT_REQUESTS); do
    if [ -f /tmp/operations_$user.log ]; then
        while read -r operation; do
            OPERATION_COUNTS[$operation]=$((${OPERATION_COUNTS[$operation]} + 1))
        done < /tmp/operations_$user.log
        rm -f /tmp/operations_$user.log
    fi
    
    if [ -f /tmp/results_$user.log ]; then
        while read -r result; do
            if [ "$result" = "SUCCESS" ]; then
                ((SUCCESS_COUNT++))
            else
                ((FAILED_COUNT++))
            fi
        done < /tmp/results_$user.log
        rm -f /tmp/results_$user.log
    fi
    
    # Clean up duration files (not used in summary currently)
    rm -f /tmp/durations_$user.log
done

LOAD_END_TIME=$(date +%s)
TOTAL_LOAD_TIME=$((LOAD_END_TIME - LOAD_START_TIME))

echo -e "\n${BLUE}========================================${NC}"
echo -e "${BLUE}  Load Test Complete!${NC}"
echo -e "${BLUE}========================================${NC}\n"

END_TIME=$(date +%s)
TOTAL_TIME=$((END_TIME - START_TIME))

echo -e "${YELLOW}📊 Test Summary:${NC}"
echo -e "  ${CYAN}Duration:${NC} ${TOTAL_TIME}s (Load test: ${TOTAL_LOAD_TIME}s)"
echo -e "  ${CYAN}Users Created:${NC} ${#USER_IDS[@]} (${#CELEBRITY_IDS[@]} celebrities)"
echo -e "  ${CYAN}Posts Created:${NC} ${#POST_IDS[@]}"
echo -e "  ${CYAN}Load Test Requests:${NC} $LOAD_TEST_ITERATIONS"
echo -e "  ${CYAN}Total Success:${NC} ${GREEN}$SUCCESS_COUNT${NC}"
echo -e "  ${CYAN}Total Failures:${NC} ${RED}$FAILED_COUNT${NC}"

if [ $TOTAL_LOAD_TIME -gt 0 ]; then
    avg_rps=$(echo "scale=2; $LOAD_TEST_ITERATIONS / $TOTAL_LOAD_TIME" | bc)
    echo -e "  ${CYAN}Average RPS:${NC} $avg_rps"
fi

echo -e "\n${YELLOW}📈 Operation Breakdown:${NC}"
for operation in "${!OPERATION_COUNTS[@]}"; do
    count=${OPERATION_COUNTS[$operation]}
    total_time=${OPERATION_TIMES[$operation]}
    if [ $count -gt 0 ] && [ $total_time -gt 0 ]; then
        avg_time=$(echo "scale=2; $total_time / $count" | bc)
        echo -e "  ${CYAN}$operation:${NC} $count requests (avg: ${avg_time}ms)"
    else
        echo -e "  ${CYAN}$operation:${NC} $count requests"
    fi
done

echo -e "\n${YELLOW}🔗 Monitoring Links:${NC}"
echo -e "  ${CYAN}Application:${NC} http://localhost:8080"
echo -e "  ${CYAN}Grafana Dashboard:${NC} http://localhost:3000"
echo -e "  ${CYAN}Prometheus Metrics:${NC} http://localhost:9090"

echo -e "\n${YELLOW}💡 Sample API Calls:${NC}"
if [ ${#USER_IDS[@]} -gt 0 ]; then
    sample_user_id=${USER_IDS[0]}
    echo -e "  ${CYAN}Get User Feed:${NC} curl '$BASE_URL/feeds/$sample_user_id?page=0&size=10'"
    echo -e "  ${CYAN}Get User Profile:${NC} curl '$BASE_URL/users/$sample_user_id'"
    echo -e "  ${CYAN}Get Followers:${NC} curl '$BASE_URL/users/$sample_user_id/followers'"
fi

if [ $FAILED_COUNT -gt 0 ]; then
    echo -e "\n${RED}⚠️  Warning: $FAILED_COUNT requests failed. Check application logs.${NC}"
else
    echo -e "\n${GREEN}🎉 All requests completed successfully!${NC}"
fi

echo -e "\n${GREEN}✅ Test data is ready for monitoring and analysis!${NC}\n"
