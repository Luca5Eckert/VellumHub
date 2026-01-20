#!/bin/bash

###############################################################################
# Run End-to-End Test for Media Recommendation System
#
# This script:
# 1. Starts all services with Docker Compose
# 2. Waits for services to be healthy
# 3. Runs the E2E test
# 4. Optionally stops services after test
###############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"
E2E_TEST="$SCRIPT_DIR/e2e_test.py"

# Check if .env file exists
if [ ! -f "$PROJECT_ROOT/.env" ]; then
    echo -e "${RED}Error: .env file not found in $PROJECT_ROOT${NC}"
    echo -e "${YELLOW}Creating default .env file...${NC}"
    
    cat > "$PROJECT_ROOT/.env" << EOF
# Database Configuration
POSTGRES_USER=admin
POSTGRES_PASSWORD=admin123

# JWT Configuration
JWT_KEY=test-secret-key-for-jwt-authentication-min-256-bits-long-key-here-for-security
JWT_EXPIRATION=86400000
EOF
    
    echo -e "${GREEN}✓ .env file created${NC}"
fi

# Function to check if a service is healthy
check_service() {
    local service_name=$1
    local url=$2
    local max_attempts=30
    local attempt=1
    
    echo -e "${BLUE}Checking $service_name...${NC}"
    
    while [ $attempt -le $max_attempts ]; do
        if curl -sf "$url" > /dev/null 2>&1; then
            echo -e "${GREEN}✓ $service_name is healthy${NC}"
            return 0
        fi
        
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    echo -e "${RED}✗ $service_name failed to become healthy${NC}"
    return 1
}

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  E2E Test Runner${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Step 1: Start Docker Compose services
echo -e "${YELLOW}[1/4] Starting Docker Compose services...${NC}"
cd "$PROJECT_ROOT"

if docker-compose ps | grep -q "Up"; then
    echo -e "${YELLOW}Services already running. Restarting...${NC}"
    docker-compose restart
else
    echo -e "${YELLOW}Starting all services...${NC}"
    docker-compose up -d
fi

echo ""

# Step 2: Wait for services to be healthy
echo -e "${YELLOW}[2/4] Waiting for services to be healthy...${NC}"
echo -e "${BLUE}This may take 1-2 minutes...${NC}"
echo ""

# Wait a bit for services to start
sleep 10

# Check each service
check_service "User Service" "http://localhost:8084/actuator/health" || echo -e "${YELLOW}Warning: User Service not responding${NC}"
check_service "Catalog Service" "http://localhost:8081/actuator/health" || echo -e "${YELLOW}Warning: Catalog Service not responding${NC}"
check_service "Engagement Service" "http://localhost:8083/actuator/health" || echo -e "${YELLOW}Warning: Engagement Service not responding${NC}"
check_service "Recommendation Service" "http://localhost:8085/actuator/health" || echo -e "${YELLOW}Warning: Recommendation Service not responding${NC}"
check_service "ML Service" "http://localhost:5000/health" || echo -e "${YELLOW}Warning: ML Service not responding${NC}"

echo ""

# Step 3: Run E2E test
echo -e "${YELLOW}[3/4] Running E2E test...${NC}"
echo ""

if [ ! -f "$E2E_TEST" ]; then
    echo -e "${RED}Error: E2E test script not found at $E2E_TEST${NC}"
    exit 1
fi

# Install required Python packages if needed
if ! python3 -c "import requests" 2>/dev/null; then
    echo -e "${YELLOW}Installing required Python packages...${NC}"
    pip3 install requests
fi

# Run the test
python3 "$E2E_TEST"
TEST_EXIT_CODE=$?

echo ""

# Step 4: Summary
echo -e "${YELLOW}[4/4] Test Summary${NC}"

if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  ✓ E2E TEST PASSED${NC}"
    echo -e "${GREEN}========================================${NC}"
else
    echo -e "${RED}========================================${NC}"
    echo -e "${RED}  ✗ E2E TEST FAILED${NC}"
    echo -e "${RED}========================================${NC}"
fi

echo ""
echo -e "${BLUE}Services are still running.${NC}"
echo -e "${BLUE}To stop them, run: docker-compose down${NC}"
echo -e "${BLUE}To view logs, run: docker-compose logs -f [service-name]${NC}"
echo ""

exit $TEST_EXIT_CODE
