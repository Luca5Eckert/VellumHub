#!/bin/bash

# Service Communication Configuration Validator
# This script validates that all communication configuration is correctly set up
# without requiring running services

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PASSED=0
FAILED=0
WARNINGS=0

echo -e "${BLUE}========================================="
echo "Service Communication Configuration Validator"
echo "=========================================${NC}"
echo ""

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

# Test 1: Verify Feign client configuration
echo -e "${BLUE}Test 1: Feign Client Configuration${NC}"

if grep -q "catalog-service.ribbon.listOfServers" "$PROJECT_ROOT/services/recommendation-service/src/main/resources/application.properties"; then
    print_result "Feign Config (application.properties)" "PASS" "Ribbon load balancer configured"
else
    print_result "Feign Config (application.properties)" "WARN" "Ribbon configuration not found"
fi

if grep -q "catalog-service.ribbon.listOfServers" "$PROJECT_ROOT/services/recommendation-service/src/main/resources/application-local.properties"; then
    print_result "Feign Config (local)" "PASS" "Local environment configured"
else
    print_result "Feign Config (local)" "WARN" "Local configuration not found"
fi

# Test 2: Verify catalog client port exists
echo ""
echo -e "${BLUE}Test 2: Catalog Client Port${NC}"

catalog_client_port="$PROJECT_ROOT/services/recommendation-service/src/main/java/com/vellumhub/recommendation_service/module/book_feature/domain/port/CatalogClient.java"

if [ -f "$catalog_client_port" ]; then
    print_result "CatalogClient Port" "PASS" "Catalog client port exists"
else
    print_result "CatalogClient Port" "WARN" "Catalog client port not found"
fi

# Test 3: Verify Kafka producer configuration
echo ""
echo -e "${BLUE}Test 3: Kafka Producer Configuration${NC}"

for service in "catalog-service" "engagement-service"; do
    props_file="$PROJECT_ROOT/services/$service/src/main/resources/application.properties"
    
    if [ -f "$props_file" ]; then
        if grep -q "spring.kafka.producer" "$props_file"; then
            print_result "$service Producer" "PASS" "Kafka producer configured"
        else
            print_result "$service Producer" "WARN" "Kafka producer config not explicit"
        fi
    else
        print_result "$service Properties" "FAIL" "application.properties not found"
    fi
done

# Test 4: Verify Kafka consumer configuration
echo ""
echo -e "${BLUE}Test 4: Kafka Consumer Configuration${NC}"

consumer_props="$PROJECT_ROOT/services/recommendation-service/src/main/resources/application.properties"

if [ -f "$consumer_props" ]; then
    if grep -q "spring.kafka.consumer" "$consumer_props"; then
        print_result "Consumer Config" "PASS" "Kafka consumer configured"
        
        if grep -q "spring.kafka.consumer.group-id" "$consumer_props"; then
            group_id=$(grep "spring.kafka.consumer.group-id" "$consumer_props" | cut -d'=' -f2-)
            print_result "Consumer Group ID" "PASS" "Group ID: $group_id"
        fi
    else
        print_result "Consumer Config" "FAIL" "Kafka consumer not configured"
    fi
else
    print_result "Consumer Properties" "FAIL" "application.properties not found"
fi

# Test 5: Verify Kafka event producers exist
echo ""
echo -e "${BLUE}Test 5: Kafka Event Producers${NC}"

if [ -f "$PROJECT_ROOT/services/catalog-service/src/main/java/com/vellumhub/catalog_service/module/book/infrastructure/producer/KafkaBookEventProducer.java" ]; then
    print_result "Catalog Event Producer" "PASS" "KafkaBookEventProducer exists"
else
    print_result "Catalog Event Producer" "FAIL" "KafkaBookEventProducer not found"
fi

if [ -f "$PROJECT_ROOT/services/engagement-service/src/main/java/com/vellumhub/engagement_service/module/rating/infrastructure/producer/KafkaEventProducer.java" ]; then
    print_result "Engagement Event Producer" "PASS" "KafkaEventProducer exists"
else
    print_result "Engagement Event Producer" "FAIL" "KafkaEventProducer not found"
fi

# Test 6: Verify Kafka event consumers exist
echo ""
echo -e "${BLUE}Test 6: Kafka Event Consumers${NC}"

consumers=(
    "CreateBookConsumerEvent"
    "UpdateBookConsumerEvent"
    "DeleteBookConsumerEvent"
    "CreatedRatingConsumerEvent"
)

for consumer in "${consumers[@]}"; do
    if find "$PROJECT_ROOT/services/recommendation-service" -name "${consumer}.java" | grep -q .; then
        print_result "$consumer" "PASS" "Consumer exists"
    else
        print_result "$consumer" "WARN" "Consumer not found (may have different name)"
    fi
done

# Test 7: Verify docker-compose network configuration
echo ""
echo -e "${BLUE}Test 7: Docker Network Configuration${NC}"

if [ -f "$PROJECT_ROOT/docker-compose.yml" ]; then
    if grep -q "vellum-network" "$PROJECT_ROOT/docker-compose.yml"; then
        print_result "Docker Network" "PASS" "vellum-network defined"
        
        # Check all services are on the same network
        cat_network_check=$(grep -A 20 "catalog-service:" "$PROJECT_ROOT/docker-compose.yml" | grep -c "vellum-network" || echo "0")
        rec_network_check=$(grep -A 20 "recommendation-service:" "$PROJECT_ROOT/docker-compose.yml" | grep -c "vellum-network" || echo "0")
        
        if [ "$cat_network_check" -gt 0 ] && [ "$rec_network_check" -gt 0 ]; then
            print_result "Network Assignment" "PASS" "Services on same network"
        else
            print_result "Network Assignment" "FAIL" "Services not all on same network"
        fi
    else
        print_result "Docker Network" "FAIL" "Network not defined"
    fi
else
    print_result "docker-compose.yml" "FAIL" "File not found"
fi

# Test 8: Verify health check endpoints are configured
echo ""
echo -e "${BLUE}Test 8: Health Check Configuration${NC}"

for service in "catalog-service" "engagement-service" "recommendation-service" "user-service"; do
    props_file="$PROJECT_ROOT/services/$service/src/main/resources/application.properties"
    
    if [ -f "$props_file" ]; then
        if grep -q "management.endpoints.web.exposure.include" "$props_file"; then
            if grep "management.endpoints.web.exposure.include" "$props_file" | grep -q "health"; then
                print_result "$service Health" "PASS" "Health endpoint exposed"
            else
                print_result "$service Health" "WARN" "Health may not be exposed"
            fi
        fi
        
        if grep -q "management.health.kafka.enabled=true" "$props_file"; then
            print_result "$service Kafka Health" "PASS" "Kafka health check enabled"
        fi
    fi
done

# Test 9: Verify communication scripts exist
echo ""
echo -e "${BLUE}Test 9: Communication Testing Tools${NC}"

if [ -f "$PROJECT_ROOT/infra/scripts/smoke-tests/test-service-communication.sh" ]; then
    print_result "Communication Test Script" "PASS" "Script exists"
    
    if [ -x "$PROJECT_ROOT/infra/scripts/smoke-tests/test-service-communication.sh" ]; then
        print_result "Script Permissions" "PASS" "Script is executable"
    else
        print_result "Script Permissions" "WARN" "Script is not executable"
    fi
else
    print_result "Communication Test Script" "FAIL" "Script not found"
fi

if [ -f "$PROJECT_ROOT/infra/scripts/kafka/kafka-health-check.sh" ]; then
    print_result "Kafka Health Script" "PASS" "Script exists"
else
    print_result "Kafka Health Script" "FAIL" "Script not found"
fi

# Test 10: Verify documentation exists
echo ""
echo -e "${BLUE}Test 10: Documentation${NC}"

if [ -f "$PROJECT_ROOT/docs/SERVICE_COMMUNICATION.md" ]; then
    print_result "Communication Guide" "PASS" "Documentation exists"
else
    print_result "Communication Guide" "WARN" "Dedicated communication guide not found"
fi

if [ -f "$PROJECT_ROOT/docs/KAFKA_MONITORING.md" ]; then
    print_result "Monitoring Guide" "PASS" "Documentation exists"
else
    print_result "Monitoring Guide" "FAIL" "Documentation not found"
fi

# Summary
echo ""
echo -e "${BLUE}========================================="
echo "Configuration Validation Summary"
echo "=========================================${NC}"
echo -e "${GREEN}Passed:${NC} $PASSED"
echo -e "${YELLOW}Warnings:${NC} $WARNINGS"
echo -e "${RED}Failed:${NC} $FAILED"
echo ""

if [ $FAILED -eq 0 ]; then
    if [ $WARNINGS -eq 0 ]; then
        echo -e "${GREEN}✓ All configuration checks passed!${NC}"
        echo ""
        echo "Communication infrastructure is properly configured."
        echo "To test with running services, use:"
        echo "  ./infra/scripts/smoke-tests/test-service-communication.sh"
        exit 0
    else
        echo -e "${YELLOW}⚠ Configuration passed with warnings${NC}"
        echo ""
        echo "Review warnings above. Most are optional or informational."
        exit 0
    fi
else
    echo -e "${RED}✗ Some configuration checks failed${NC}"
    echo ""
    echo "Please review and fix the failed items above."
    exit 1
fi
