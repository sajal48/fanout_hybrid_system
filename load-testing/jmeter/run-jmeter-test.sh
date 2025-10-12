#!/bin/bash

# JMeter Load Test Runner for Twitter Feed System
# Usage: ./run-jmeter-test.sh [OPTIONS]

set -e

# Default values
JMETER_HOME=""
TEST_FILE="twitter-feed-load-test.jmx"
RESULTS_DIR="results"
REPORT_NAME="load-test-$(date +%Y%m%d-%H%M%S)"
GUI_MODE=false
CLEANUP=false

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to display help
show_help() {
    cat << EOF
JMeter Load Test Runner for Twitter Feed System

Usage: $0 [OPTIONS]

OPTIONS:
    -h, --help              Show this help message
    -g, --gui               Run in GUI mode (default: command line)
    -j, --jmeter-home PATH  JMeter installation directory
    -t, --test-file FILE    Test plan file (default: twitter-feed-load-test.jmx)
    -r, --results DIR       Results directory (default: results)
    -n, --name NAME         Report name (default: load-test-TIMESTAMP)
    -c, --cleanup           Clean up old results before running
    --threads N             Override number of threads (users)
    --rampup N              Override ramp-up time in seconds
    --duration N            Override test duration in seconds

EXAMPLES:
    $0                      # Run test in command line mode
    $0 --gui               # Run test in GUI mode
    $0 --cleanup           # Clean old results and run test
    $0 --threads 100       # Run with 100 concurrent users
    
PREREQUISITES:
    - JMeter installed and accessible
    - Twitter Feed System running on localhost:8080
    - Java 8+ installed

EOF
}

# Function to find JMeter installation
find_jmeter() {
    if [[ -n "$JMETER_HOME" && -f "$JMETER_HOME/bin/jmeter" ]]; then
        return 0
    fi
    
    # Common JMeter installation paths
    local common_paths=(
        "/usr/local/jmeter"
        "/opt/jmeter"
        "/usr/share/jmeter"
        "$HOME/apache-jmeter"
        "$HOME/Tools/apache-jmeter"
        "/Applications/JMeter"
    )
    
    # Check if jmeter is in PATH
    if command -v jmeter >/dev/null 2>&1; then
        JMETER_HOME=$(dirname $(dirname $(which jmeter)))
        return 0
    fi
    
    # Search common paths
    for path in "${common_paths[@]}"; do
        if [[ -f "$path/bin/jmeter" ]]; then
            JMETER_HOME="$path"
            return 0
        fi
        
        # Check for versioned directories
        if [[ -d "$path" ]]; then
            local versioned=$(find "$path"* -name "jmeter" -path "*/bin/jmeter" 2>/dev/null | head -1)
            if [[ -n "$versioned" ]]; then
                JMETER_HOME=$(dirname $(dirname "$versioned"))
                return 0
            fi
        fi
    done
    
    return 1
}

# Function to check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."
    
    # Check if test file exists
    if [[ ! -f "$TEST_FILE" ]]; then
        print_error "Test file not found: $TEST_FILE"
        exit 1
    fi
    
    # Check if data directory exists
    if [[ ! -d "data" ]]; then
        print_error "Data directory not found. Ensure CSV files are in 'data/' directory"
        exit 1
    fi
    
    # Find JMeter installation
    if ! find_jmeter; then
        print_error "JMeter installation not found!"
        print_error "Please install JMeter or specify path with --jmeter-home"
        print_error "Download from: https://jmeter.apache.org/download_jmeter.cgi"
        exit 1
    fi
    
    print_success "Found JMeter at: $JMETER_HOME"
    
    # Check Java version
    if ! command -v java >/dev/null 2>&1; then
        print_error "Java not found. JMeter requires Java 8+"
        exit 1
    fi
    
    local java_version=$(java -version 2>&1 | grep "version" | cut -d'"' -f2 | cut -d'.' -f1-2)
    print_success "Java version: $java_version"
    
    # Check if application is running
    print_info "Checking if Twitter Feed System is running..."
    if curl -s --connect-timeout 5 http://localhost:8080/actuator/health >/dev/null 2>&1; then
        print_success "Application is running on localhost:8080"
    else
        print_warning "Application may not be running on localhost:8080"
        print_warning "Please ensure the Twitter Feed System is started"
    fi
}

# Function to setup results directory
setup_results() {
    if [[ "$CLEANUP" == true && -d "$RESULTS_DIR" ]]; then
        print_info "Cleaning up old results..."
        rm -rf "$RESULTS_DIR"
    fi
    
    mkdir -p "$RESULTS_DIR"
    print_success "Results directory ready: $RESULTS_DIR"
}

# Function to run JMeter test
run_jmeter_test() {
    local jmeter_bin="$JMETER_HOME/bin/jmeter"
    local results_file="$RESULTS_DIR/${REPORT_NAME}.jtl"
    local html_report_dir="$RESULTS_DIR/${REPORT_NAME}-report"
    
    if [[ "$GUI_MODE" == true ]]; then
        print_info "Starting JMeter in GUI mode..."
        print_info "1. The test plan will open in JMeter GUI"
        print_info "2. Click the green 'Start' button to run the test"
        print_info "3. Monitor results in the listeners"
        
        "$jmeter_bin" -t "$TEST_FILE"
    else
        print_info "Starting JMeter load test in command line mode..."
        print_info "Test plan: $TEST_FILE"
        print_info "Results file: $results_file"
        print_info "HTML report: $html_report_dir"
        
        # Run the test
        "$jmeter_bin" -n -t "$TEST_FILE" -l "$results_file" -e -o "$html_report_dir"
        
        print_success "Load test completed!"
        print_success "Results saved to: $results_file"
        print_success "HTML report generated: $html_report_dir/index.html"
        
        # Display summary
        if [[ -f "$results_file" ]]; then
            print_info "Quick Summary:"
            local total_samples=$(tail -n +2 "$results_file" | wc -l)
            local successful_samples=$(tail -n +2 "$results_file" | awk -F',' '$8=="true"' | wc -l)
            local error_rate=$(echo "scale=2; (($total_samples - $successful_samples) * 100) / $total_samples" | bc -l 2>/dev/null || echo "N/A")
            
            echo "  Total Samples: $total_samples"
            echo "  Successful: $successful_samples"
            echo "  Error Rate: ${error_rate}%"
        fi
        
        # Open HTML report if on macOS or Linux with GUI
        if command -v open >/dev/null 2>&1; then
            print_info "Opening HTML report..."
            open "$html_report_dir/index.html"
        elif command -v xdg-open >/dev/null 2>&1; then
            print_info "Opening HTML report..."
            xdg-open "$html_report_dir/index.html"
        else
            print_info "Open the HTML report manually: $html_report_dir/index.html"
        fi
    fi
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -g|--gui)
            GUI_MODE=true
            shift
            ;;
        -j|--jmeter-home)
            JMETER_HOME="$2"
            shift 2
            ;;
        -t|--test-file)
            TEST_FILE="$2"
            shift 2
            ;;
        -r|--results)
            RESULTS_DIR="$2"
            shift 2
            ;;
        -n|--name)
            REPORT_NAME="$2"
            shift 2
            ;;
        -c|--cleanup)
            CLEANUP=true
            shift
            ;;
        --threads)
            # This would require modifying the JMX file or using JMeter properties
            print_warning "Thread override not implemented yet. Modify the JMX file directly."
            shift 2
            ;;
        --rampup)
            print_warning "Ramp-up override not implemented yet. Modify the JMX file directly."
            shift 2
            ;;
        --duration)
            print_warning "Duration override not implemented yet. Modify the JMX file directly."
            shift 2
            ;;
        *)
            print_error "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

# Main execution
main() {
    echo "========================================="
    echo "  JMeter Load Test Runner"
    echo "  Twitter Feed System"
    echo "========================================="
    
    check_prerequisites
    setup_results
    run_jmeter_test
    
    echo ""
    echo "========================================="
    echo "  Test Execution Complete!"
    echo "========================================="
    echo ""
    echo "📊 Next Steps:"
    echo "  1. Review HTML report for detailed analysis"
    echo "  2. Check Grafana dashboard: http://localhost:3000"
    echo "  3. Monitor application logs for any errors"
    echo "  4. Compare results with performance baselines"
    echo ""
    echo "🔗 Useful Links:"
    echo "  Application: http://localhost:8080"
    echo "  Grafana: http://localhost:3000/d/twitter-feed-metrics/application-metrics"
    echo "  Prometheus: http://localhost:9090"
}

# Run main function
main