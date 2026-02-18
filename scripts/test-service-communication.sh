#!/bin/bash

# Service Communication Verification Script
# This script tests all communication pathways between VellumHub microservices

# Note: We don't use 'set -e' here because we want to test all services
# and report results even if some tests fail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Color codes for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Service URLs (adjust these based on environment)
USER_SERVICE_URL="${USER_SERVICE_URL:-http://localhost:8084}"
CATALOG_SERVICE_URL="${CATALOG_SERVICE_URL:-http://localhost:8081}"
ENGAGEMENT_SERVICE_URL="${ENGAGEMENT_SERVICE_URL:-http://localhost:8083}"
RECOMMENDATION_SERVICE_URL="${RECOMMENDATION_SERVICE_URL:-http://localhost:8085}"

# Kafka settings
KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka}"
KAFKA_BROKER="${KAFKA_BROKER:-localhost:9092}"

# Test results
PASSED=0
FAILED=0
WARNINGS=0

echo -e "${BLUE}========================================="
echo "VellumHub Service Communication Test"
echo "=========================================${NC}"
echo ""

# Function to print test results
print_result() {
    local test_name=$1
    local result=$2
    local message=$3
    
    if [ "$result" = "PASS" ]; then
        echo -e "${GREEN}✓${NC} $test_name: $message"
        ((PASSED++))
    elif [ "$result" = "FAIL" ]; then
        echo -e "${RED}✗${NC} $test_name: $message"
        ((FAILED++))
    else
        echo -e "${YELLOW}⚠${NC} $test_name: $message"
        ((WARNINGS++))
    fi
}

# Function to check if service is healthy
check_service_health() {
    local service_name=$1
    local service_url=$2
    
    echo ""
    echo -e "${BLUE}Testing $service_name Health...${NC}"
    
    # Check if service is responding
    if curl -sf "$service_url/actuator/health" > /dev/null 2>&1; then
        local health_status=$(curl -s "$service_url/actuator/health" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
        if [ "$health_status" = "UP" ]; then
            print_result "$service_name" "PASS" "Service is UP and healthy"
            
            # Check Kafka health if available
            local kafka_health=$(curl -s "$service_url/actuator/health" | grep -o '"kafka":{"status":"[^"]*"' | cut -d'"' -f6)
            if [ ! -z "$kafka_health" ]; then
                if [ "$kafka_health" = "UP" ]; then
                    print_result "$service_name Kafka" "PASS" "Kafka connection is UP"
                else
                    print_result "$service_name Kafka" "FAIL" "Kafka connection is $kafka_health"
                fi
            fi
            return 0
        else
            print_result "$service_name" "FAIL" "Service status is $health_status"
            return 1
        fi
    else
        print_result "$service_name" "FAIL" "Service is not responding"
        return 1
    fi
}

# Function to test Kafka topics
test_kafka_topics() {
    echo ""
    echo -e "${BLUE}Testing Kafka Topics...${NC}"
    
    if ! docker ps | grep -q "$KAFKA_CONTAINER"; then
        print_result "Kafka Container" "FAIL" "Kafka container is not running"
        return 1
    fi
    
    print_result "Kafka Container" "PASS" "Kafka container is running"
    
    # List topics
    local topics=$(docker exec "$KAFKA_CONTAINER" kafka-topics --bootstrap-server "$KAFKA_BROKER" --list 2>/dev/null || echo "")
    
    # Expected topics
    local expected_topics=("created-book" "updated-book" "deleted-book" "created-rating")
    
    for topic in "${expected_topics[@]}"; do
        if echo "$topics" | grep -q "^$topic$"; then
            print_result "Kafka Topic: $topic" "PASS" "Topic exists"
        else
            print_result "Kafka Topic: $topic" "WARN" "Topic not found (may be created on first use)"
        fi
    done
}

# Function to test Kafka consumer groups
test_kafka_consumers() {
    echo ""
    echo -e "${BLUE}Testing Kafka Consumer Groups...${NC}"
    
    if ! docker ps | grep -q "$KAFKA_CONTAINER"; then
        print_result "Kafka Consumers" "FAIL" "Kafka container is not running"
        return 1
    fi
    
    local groups=$(docker exec "$KAFKA_CONTAINER" kafka-consumer-groups --bootstrap-server "$KAFKA_BROKER" --list 2>/dev/null || echo "")
    
    if echo "$groups" | grep -q "recommendation-service"; then
        print_result "Consumer Group" "PASS" "recommendation-service consumer group exists"
        
        # Get consumer group details
        local group_details=$(docker exec "$KAFKA_CONTAINER" kafka-consumer-groups --bootstrap-server "$KAFKA_BROKER" --describe --group recommendation-service-group 2>/dev/null || echo "")
        
        if [ ! -z "$group_details" ]; then
            local lag=$(echo "$group_details" | awk 'NR>1 {sum+=$5} END {print sum}')
            if [ -z "$lag" ] || [ "$lag" = "0" ] || [ "$lag" = "-" ]; then
                print_result "Consumer Lag" "PASS" "No lag detected (consumers are up-to-date)"
            else
                print_result "Consumer Lag" "WARN" "Consumer lag: $lag messages"
            fi
        fi
    else
        print_result "Consumer Group" "WARN" "No consumer groups found (services may not have consumed messages yet)"
    fi
}

# Function to test REST communication (Feign Client)
test_feign_communication() {
    echo ""
    echo -e "${BLUE}Testing REST Communication (Feign Client)...${NC}"
    
    # Check if recommendation service can reach catalog service
    # This is indirect - we check if both services are in the same network
    
    if docker ps --format '{{.Names}}' | grep -q "recommendation-service"; then
        if docker ps --format '{{.Names}}' | grep -q "catalog-service"; then
            # Check if they're on the same network
            local rec_network=$(docker inspect recommendation-service --format '{{range $key, $value := .NetworkSettings.Networks}}{{$key}}{{end}}' 2>/dev/null)
            local cat_network=$(docker inspect catalog-service --format '{{range $key, $value := .NetworkSettings.Networks}}{{$key}}{{end}}' 2>/dev/null)
            
            if [ "$rec_network" = "$cat_network" ]; then
                print_result "Service Network" "PASS" "Services are on the same Docker network ($rec_network)"
                
                # Test if recommendation-service can reach catalog-service
                if docker exec recommendation-service ping -c 1 catalog-service > /dev/null 2>&1; then
                    print_result "Network Connectivity" "PASS" "Recommendation service can reach Catalog service"
                else
                    print_result "Network Connectivity" "WARN" "Could not ping catalog-service from recommendation-service"
                fi
            else
                print_result "Service Network" "FAIL" "Services are on different networks"
            fi
        else
            print_result "Catalog Service" "WARN" "Catalog service container not running"
        fi
    else
        print_result "Recommendation Service" "WARN" "Recommendation service container not running"
    fi
}

# Function to test service endpoints
test_service_endpoints() {
    echo ""
    echo -e "${BLUE}Testing Service Endpoints...${NC}"
    
    # Test catalog service bulk endpoint (used by Feign client)
    if curl -sf "$CATALOG_SERVICE_URL/actuator/health" > /dev/null 2>&1; then
        # Try to access the bulk endpoint with empty array (should return 200 or 400, not 404)
        local response_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
            "$CATALOG_SERVICE_URL/api/book/bulk" \
            -H "Content-Type: application/json" \
            -d "[]" 2>/dev/null || echo "000")
        
        if [ "$response_code" = "200" ] || [ "$response_code" = "400" ] || [ "$response_code" = "401" ] || [ "$response_code" = "403" ]; then
            print_result "Catalog Bulk Endpoint" "PASS" "Endpoint is accessible (HTTP $response_code)"
        elif [ "$response_code" = "404" ]; then
            print_result "Catalog Bulk Endpoint" "FAIL" "Endpoint not found (HTTP 404)"
        else
            print_result "Catalog Bulk Endpoint" "WARN" "Unexpected response code: $response_code"
        fi
    else
        print_result "Catalog Bulk Endpoint" "WARN" "Catalog service not accessible"
    fi
}

# Function to generate test summary
print_summary() {
    echo ""
    echo -e "${BLUE}========================================="
    echo "Test Summary"
    echo "=========================================${NC}"
    echo -e "${GREEN}Passed:${NC} $PASSED"
    echo -e "${YELLOW}Warnings:${NC} $WARNINGS"
    echo -e "${RED}Failed:${NC} $FAILED"
    echo ""
    
    if [ $FAILED -eq 0 ]; then
        if [ $WARNINGS -eq 0 ]; then
            echo -e "${GREEN}All tests passed! ✓${NC}"
            return 0
        else
            echo -e "${YELLOW}Tests passed with warnings ⚠${NC}"
            return 0
        fi
    else
        echo -e "${RED}Some tests failed ✗${NC}"
        return 1
    fi
}

# Main test execution
main() {
    echo "Testing service communication patterns..."
    echo "Environment: ${ENVIRONMENT:-local}"
    echo ""
    
    # Test 1: Check all service health
    check_service_health "User Service" "$USER_SERVICE_URL"
    check_service_health "Catalog Service" "$CATALOG_SERVICE_URL"
    check_service_health "Engagement Service" "$ENGAGEMENT_SERVICE_URL"
    check_service_health "Recommendation Service" "$RECOMMENDATION_SERVICE_URL"
    
    # Test 2: Kafka topics
    test_kafka_topics
    
    # Test 3: Kafka consumer groups
    test_kafka_consumers
    
    # Test 4: REST/Feign communication
    test_feign_communication
    
    # Test 5: Service endpoints
    test_service_endpoints
    
    # Print summary
    print_summary
}

# Run main function
main
exit_code=$?

echo ""
echo "For more details on monitoring, see:"
echo "  - Kafka UI: http://localhost:8090"
echo "  - Service Health: curl http://localhost:808{1,3,4,5}/actuator/health"
echo "  - Documentation: docs/KAFKA_MONITORING.md"

exit $exit_code
