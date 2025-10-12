#!/bin/bash

# EXTREME LOAD TEST - 500,000 CONCURRENT USERS
# =============================================
# WARNING: This is an enterprise-grade extreme load test
# Ensure your system has sufficient resources:
# - At least 32GB RAM recommended
# - High-performance CPU (16+ cores)
# - SSD storage
# - Network bandwidth to handle ~2M+ requests

echo "🚨 EXTREME LOAD TEST WARNING 🚨"
echo "================================="
echo "This test will simulate 500,000 concurrent users"
echo "Expected total requests: ~2,200,000"
echo "Estimated duration: 45-60 minutes"
echo "Monitor system resources closely!"
echo ""
echo "📊 Load Distribution:"
echo "- Browse Users: 300,000 × 5 loops = 1,500,000 feed requests"
echo "- Active Users: 100,000 × 3 loops = 300,000 post requests"
echo "- Celebrity Consumers: 100,000 × 4 loops = 400,000 celebrity feed requests"
echo ""
echo "⏱️ Ramp-up Times:"
echo "- Browse Users: 3,600 seconds (1 hour gradual ramp)"
echo "- Active Users: 1,800 seconds (30 min gradual ramp)"
echo "- Celebrity Consumers: 2,400 seconds (40 min gradual ramp)"
echo ""
echo "🖥️ System Requirements Check:"

# Check available memory
total_memory=$(free -m | awk 'NR==2{printf "%.1f", $2/1024}')
available_memory=$(free -m | awk 'NR==2{printf "%.1f", $7/1024}')
echo "- Total Memory: ${total_memory}GB"
echo "- Available Memory: ${available_memory}GB"

if (( $(echo "${available_memory} < 16" | bc -l) )); then
    echo "⚠️  WARNING: Less than 16GB available memory detected!"
    echo "   Consider reducing thread count or adding more RAM"
fi

# Check CPU cores
cpu_cores=$(nproc)
echo "- CPU Cores: ${cpu_cores}"

if [ ${cpu_cores} -lt 8 ]; then
    echo "⚠️  WARNING: Less than 8 CPU cores detected!"
    echo "   Performance may be limited with extreme load"
fi

# Check disk space
disk_space=$(df -h . | awk 'NR==2 {print $4}')
echo "- Available Disk Space: ${disk_space}"

echo ""
echo "🎯 Monitor URLs:"
echo "- Grafana Metrics: http://localhost:3000/d/twitter-feed-metrics/application-metrics"
echo "- JMeter GUI: Use 'jmeter -t twitter-feed-load-test.jmx' for GUI monitoring"
echo ""

read -p "⚡ Continue with EXTREME load test? [y/N]: " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Test cancelled"
    exit 0
fi

echo ""
echo "🚀 STARTING EXTREME LOAD TEST..."
echo "=================================="

# Set extreme JVM settings for massive load
export HEAP="-Xms16g -Xmx32g"
export JVM_ARGS="-XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:G1HeapRegionSize=16m -Djava.net.useSystemProxies=false"

# Run JMeter with extreme load configuration
echo "⚡ Launching JMeter with extreme memory settings..."
echo "Heap: $HEAP"
echo "JVM Args: $JVM_ARGS"

jmeter -n -t twitter-feed-load-test.jmx \
    -l results/extreme-load-test-results-$(date +%Y%m%d-%H%M%S).jtl \
    -e -o results/extreme-load-test-report-$(date +%Y%m%d-%H%M%S)/ \
    -Jjmeter.save.saveservice.output_format=csv \
    -Jjmeter.save.saveservice.response_data=false \
    -Jjmeter.save.saveservice.samplerData=false \
    -Jjmeter.save.saveservice.requestHeaders=false \
    -Jjmeter.save.saveservice.responseHeaders=false \
    -Djava.net.preferIPv4Stack=true

echo ""
echo "🏁 EXTREME LOAD TEST COMPLETED!"
echo "================================"
echo "Results saved in: results/"
echo "📈 Check Grafana for live metrics: http://localhost:3000"