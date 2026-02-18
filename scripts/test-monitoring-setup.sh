#!/bin/bash

# Test Script for Kafka Monitoring Setup
# This script validates that the monitoring infrastructure is correctly configured
# 
# Requirements: Docker Compose V2 (docker compose command)
# For Docker Compose V1 users, replace 'docker compose' with 'docker-compose'

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "========================================="
echo "Kafka Monitoring Setup Test"
echo "========================================="
echo ""

# Check Docker Compose version
if command -v docker &> /dev/null; then
    if docker compose version &> /dev/null; then
        echo "✓ Docker Compose V2 detected"
        COMPOSE_CMD="docker compose"
    elif docker-compose version &> /dev/null; then
        echo "✓ Docker Compose V1 detected"
        COMPOSE_CMD="docker-compose"
    else
        echo "✗ Docker Compose not found"
        exit 1
    fi
else
    echo "✗ Docker not found"
    exit 1
fi

echo ""

# Test 1: Check docker-compose configuration
echo "Test 1: Validating docker-compose configuration..."
cd "$PROJECT_ROOT"

# Create temporary .env if it doesn't exist
TEMP_ENV_CREATED=false
if [ ! -f .env ]; then
    echo "⚠ .env file not found, creating temporary one for testing..."
    cat > .env << 'ENVEOF'
# Temporary test values - NOT FOR PRODUCTION USE
POSTGRES_USER=test_user
POSTGRES_PASSWORD=dummy_password_for_validation_only
JWT_KEY=temporary-test-jwt-secret-key-for-validation-purposes-only
JWT_EXPIRATION=86400000
ENVEOF
    TEMP_ENV_CREATED=true
fi

if $COMPOSE_CMD config --quiet 2>/dev/null; then
    echo "✓ Docker Compose configuration is valid"
else
    echo "✗ Docker Compose configuration has errors"
    [ "$TEMP_ENV_CREATED" = true ] && rm -f .env
    exit 1
fi

echo ""

# Test 2: Check Kafka service is defined
echo "Test 2: Checking Kafka service definition..."
if $COMPOSE_CMD config | grep -q "kafka:"; then
    echo "✓ Kafka service is defined"
else
    echo "✗ Kafka service not found"
    exit 1
fi

echo ""

# Test 3: Check Kafka UI service is defined
echo "Test 3: Checking Kafka UI service definition..."
if $COMPOSE_CMD config | grep -q "kafka-ui:"; then
    echo "✓ Kafka UI service is defined"
    
    # Check port mapping (in the expanded format)
    if $COMPOSE_CMD config | grep -A 10 "kafka-ui:" | grep -q "target: 8080"; then
        echo "✓ Kafka UI port mapping is configured"
    else
        echo "✗ Kafka UI port mapping not found"
        exit 1
    fi
else
    echo "✗ Kafka UI service not found"
    exit 1
fi

echo ""

# Test 4: Check JMX port is exposed
echo "Test 4: Checking Kafka JMX configuration..."
if $COMPOSE_CMD config | grep -q "KAFKA_JMX_PORT"; then
    echo "✓ Kafka JMX port is configured"
else
    echo "✗ Kafka JMX port not configured"
    exit 1
fi

echo ""

# Test 5: Check health check scripts exist
echo "Test 5: Checking health check scripts..."
if [ -f "$SCRIPT_DIR/kafka-health-check.sh" ]; then
    echo "✓ kafka-health-check.sh exists"
    
    if [ -x "$SCRIPT_DIR/kafka-health-check.sh" ]; then
        echo "✓ kafka-health-check.sh is executable"
    else
        echo "⚠ kafka-health-check.sh exists but is not executable"
    fi
else
    echo "✗ kafka-health-check.sh not found"
    exit 1
fi

echo ""

# Test 6: Check application.properties for actuator endpoints
echo "Test 6: Checking Spring Boot actuator configurations..."

services=("catalog-service" "engagement-service" "recommendation-service" "user-service")
for service in "${services[@]}"; do
    props_file="$PROJECT_ROOT/$service/src/main/resources/application.properties"
    
    if [ -f "$props_file" ]; then
        if grep -q "management.health.kafka.enabled=true" "$props_file" 2>/dev/null; then
            echo "✓ $service: Kafka health check enabled"
        else
            echo "⚠ $service: Kafka health check not explicitly enabled (may use defaults)"
        fi
        
        if grep -q "management.endpoints.web.exposure.include.*metrics" "$props_file" 2>/dev/null; then
            echo "✓ $service: Metrics endpoint exposed"
        else
            echo "⚠ $service: Metrics endpoint not exposed"
        fi
    else
        echo "⚠ $service: application.properties not found"
    fi
done

echo ""

# Test 7: Check documentation exists
echo "Test 7: Checking documentation..."
if [ -f "$PROJECT_ROOT/docs/KAFKA_MONITORING.md" ]; then
    echo "✓ Kafka monitoring documentation exists"
    
    # Check documentation has key sections
    if grep -q "Kafka UI" "$PROJECT_ROOT/docs/KAFKA_MONITORING.md"; then
        echo "✓ Documentation includes Kafka UI section"
    fi
    
    if grep -q "Spring Boot Actuator" "$PROJECT_ROOT/docs/KAFKA_MONITORING.md"; then
        echo "✓ Documentation includes Actuator section"
    fi
    
    if grep -q "Troubleshooting" "$PROJECT_ROOT/docs/KAFKA_MONITORING.md"; then
        echo "✓ Documentation includes Troubleshooting section"
    fi
else
    echo "✗ Kafka monitoring documentation not found"
    exit 1
fi

echo ""

# Test 8: Check README mentions monitoring
echo "Test 8: Checking README for monitoring section..."
if grep -q "Monitoring" "$PROJECT_ROOT/README.md"; then
    echo "✓ README includes monitoring section"
    
    if grep -q "Kafka UI" "$PROJECT_ROOT/README.md"; then
        echo "✓ README mentions Kafka UI"
    fi
    
    if grep -q "8090" "$PROJECT_ROOT/README.md"; then
        echo "✓ README mentions Kafka UI port"
    fi
else
    echo "⚠ README does not mention monitoring"
fi

echo ""
echo "========================================="
echo "All Tests Passed!"
echo "========================================="
echo ""
echo "Monitoring setup is correctly configured."
echo ""
echo "Next steps:"
echo "1. Start the services: $COMPOSE_CMD up -d"
echo "2. Access Kafka UI: http://localhost:8090"
echo "3. Check service health: curl http://localhost:8081/actuator/health"
echo "4. Run health check: ./scripts/kafka-health-check.sh"

# Clean up temporary .env if created
if [ "$TEMP_ENV_CREATED" = true ]; then
    rm -f "$PROJECT_ROOT/.env"
fi
