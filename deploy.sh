#!/bin/bash

# ============================================================================
# Twitter Feed System - Docker Deployment Script
# ============================================================================
# This script manages the Docker Compose deployment for the hybrid feed system
# Usage: ./deploy.sh [start|stop|restart|status|logs|clean]
# ============================================================================

set -e  # Exit on error

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ============================================================================
# Helper Functions
# ============================================================================

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

# ============================================================================
# Check Prerequisites
# ============================================================================

check_prerequisites() {
    print_info "Checking prerequisites..."

    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed. Please install Docker first."
        exit 1
    fi

    if ! docker compose version &> /dev/null; then
        print_error "Docker Compose is not installed or 'docker compose' command is not available."
        exit 1
    fi

    print_success "All prerequisites are met"
}

# ============================================================================
# Start Services
# ============================================================================

start_services() {
    print_header "Starting Twitter Feed System Services"

    check_prerequisites

    print_info "Creating necessary directories..."
    mkdir -p docker/postgres docker/cassandra docker/redis

    print_info "Starting Docker containers..."
    docker compose up -d

    print_success "Containers started successfully"

    print_info "Waiting for services to be healthy..."
    echo ""

    # Wait for PostgreSQL
    print_info "Waiting for PostgreSQL to be ready..."
    timeout 60 bash -c 'until docker compose exec -T postgres pg_isready -U twitter_admin -d twitter_feed > /dev/null 2>&1; do sleep 2; done' || {
        print_error "PostgreSQL failed to start in time"
        exit 1
    }
    print_success "PostgreSQL is ready"

    # Wait for Redis
    print_info "Waiting for Redis to be ready..."
    timeout 60 bash -c 'until docker compose exec -T redis redis-cli ping > /dev/null 2>&1; do sleep 2; done' || {
        print_error "Redis failed to start in time"
        exit 1
    }
    print_success "Redis is ready"

    # Wait for Cassandra
    print_info "Waiting for Cassandra to be ready (this may take a minute)..."
    timeout 120 bash -c 'until docker compose exec -T cassandra cqlsh -e "DESCRIBE CLUSTER" > /dev/null 2>&1; do sleep 5; done' || {
        print_error "Cassandra failed to start in time"
        exit 1
    }
    print_success "Cassandra is ready"

    # Wait for Cassandra initialization
    print_info "Waiting for Cassandra initialization to complete..."
    sleep 10

    print_header "Deployment Complete!"
    print_success "All services are running and healthy"
    echo ""
    print_info "Service URLs:"
    echo "  PostgreSQL: localhost:5432"
    echo "  Redis:      localhost:6379"
    echo "  Cassandra:  localhost:9042"
    echo ""
    print_info "Database Credentials:"
    echo "  PostgreSQL - DB: twitter_feed, User: twitter_admin, Password: twitter_password_123"
    echo ""
    print_info "Use './deploy.sh status' to check service status"
    print_info "Use './deploy.sh logs' to view logs"
}

# ============================================================================
# Stop Services
# ============================================================================

stop_services() {
    print_header "Stopping Twitter Feed System Services"

    print_info "Stopping Docker containers..."
    docker compose down

    print_success "All services stopped successfully"
}

# ============================================================================
# Restart Services
# ============================================================================

restart_services() {
    print_header "Restarting Twitter Feed System Services"

    stop_services
    sleep 3
    start_services
}

# ============================================================================
# Show Status
# ============================================================================

show_status() {
    print_header "Twitter Feed System Status"

    docker compose ps

    echo ""
    print_info "Checking service health..."

    # Check PostgreSQL
    if docker compose exec -T postgres pg_isready -U twitter_admin -d twitter_feed > /dev/null 2>&1; then
        print_success "PostgreSQL is healthy"
    else
        print_error "PostgreSQL is not responding"
    fi

    # Check Redis
    if docker compose exec -T redis redis-cli ping > /dev/null 2>&1; then
        print_success "Redis is healthy"
    else
        print_error "Redis is not responding"
    fi

    # Check Cassandra
    if docker compose exec -T cassandra cqlsh -e "DESCRIBE CLUSTER" > /dev/null 2>&1; then
        print_success "Cassandra is healthy"
    else
        print_error "Cassandra is not responding"
    fi
}

# ============================================================================
# Show Logs
# ============================================================================

show_logs() {
    SERVICE=${1:-}

    if [ -z "$SERVICE" ]; then
        print_info "Showing logs for all services (Ctrl+C to exit)..."
        docker compose logs -f
    else
        print_info "Showing logs for $SERVICE (Ctrl+C to exit)..."
        docker compose logs -f "$SERVICE"
    fi
}

# ============================================================================
# Clean Everything
# ============================================================================

clean_all() {
    print_header "Cleaning Twitter Feed System"

    print_warning "This will remove all containers, volumes, and data!"
    read -p "Are you sure? (yes/no): " -r
    echo

    if [[ $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
        print_info "Stopping and removing containers..."
        docker compose down -v

        print_success "Cleanup complete"
    else
        print_info "Cleanup cancelled"
    fi
}

# ============================================================================
# Connect to Database
# ============================================================================

connect_postgres() {
    print_info "Connecting to PostgreSQL..."
    docker compose exec postgres psql -U twitter_admin -d twitter_feed
}

connect_cassandra() {
    print_info "Connecting to Cassandra..."
    docker compose exec cassandra cqlsh
}

connect_redis() {
    print_info "Connecting to Redis..."
    docker compose exec redis redis-cli
}

# ============================================================================
# Show Help
# ============================================================================

show_help() {
    echo "Twitter Feed System - Docker Deployment Script"
    echo ""
    echo "Usage: ./deploy.sh [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  start              Start all services"
    echo "  stop               Stop all services"
    echo "  restart            Restart all services"
    echo "  status             Show service status"
    echo "  logs [service]     Show logs (all or specific service)"
    echo "  clean              Remove all containers and volumes"
    echo "  postgres           Connect to PostgreSQL CLI"
    echo "  cassandra          Connect to Cassandra CQL shell"
    echo "  redis              Connect to Redis CLI"
    echo "  help               Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./deploy.sh start"
    echo "  ./deploy.sh logs postgres"
    echo "  ./deploy.sh status"
}

# ============================================================================
# Main Script Logic
# ============================================================================

case "${1:-}" in
    start)
        start_services
        ;;
    stop)
        stop_services
        ;;
    restart)
        restart_services
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs "${2:-}"
        ;;
    clean)
        clean_all
        ;;
    postgres)
        connect_postgres
        ;;
    cassandra)
        connect_cassandra
        ;;
    redis)
        connect_redis
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        print_error "Invalid command: ${1:-}"
        echo ""
        show_help
        exit 1
        ;;
esac
