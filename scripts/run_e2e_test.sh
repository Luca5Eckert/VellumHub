#!/bin/bash

###############################################################################
# Comprehensive E2E Test Runner with Service Orchestration
#
# This script:
# 1. Starts all services with Docker Compose
# 2. Waits for services to be truly ready (not just "up")
# 3. Seeds test data into the database
# 4. Runs the E2E test
# 5. Reports results
###############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"
E2E_TEST="$SCRIPT_DIR/e2e_test.py"
SEED_SCRIPT="$SCRIPT_DIR/seed-e2e-data.sql"

# Maximum wait time for services (in seconds)
MAX_WAIT_TIME=180
HEALTH_CHECK_INTERVAL=5

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  E2E Test Runner Completo${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if .env file exists
if [ ! -f "$PROJECT_ROOT/.env" ]; then
    echo -e "${YELLOW}Warning: .env file not found in $PROJECT_ROOT${NC}"
    echo -e "${YELLOW}Creating default .env file...${NC}"
    
    cat > "$PROJECT_ROOT/.env" << EOF
# Database Configuration
POSTGRES_USER=admin
POSTGRES_PASSWORD=admin123

# JWT Configuration
# NOTE: This is a TEST-ONLY key. NEVER use this in production!
# Both JWT_KEY and JWT_SECRET must be set to the same value for all services
JWT_KEY=test-secret-key-for-jwt-authentication-min-256-bits-long-key-here-for-security
JWT_SECRET=test-secret-key-for-jwt-authentication-min-256-bits-long-key-here-for-security
JWT_EXPIRATION=86400000
EOF
    
    echo -e "${GREEN}✓ .env file created${NC}"
fi

# Function to wait for a service to be ready
wait_for_service() {
    local service_name=$1
    local url=$2
    local max_attempts=$((MAX_WAIT_TIME / HEALTH_CHECK_INTERVAL))
    local attempt=1
    
    echo -e "${CYAN}Waiting for $service_name to be ready...${NC}"
    
    while [ $attempt -le $max_attempts ]; do
        if curl -sf "$url" > /dev/null 2>&1; then
            echo -e "${GREEN}✓ $service_name is ready${NC}"
            return 0
        fi
        
        echo -ne "${YELLOW}  Attempt $attempt/$max_attempts...${NC}\r"
        sleep $HEALTH_CHECK_INTERVAL
        attempt=$((attempt + 1))
    done
    
    echo -e "${RED}✗ $service_name failed to become ready after ${MAX_WAIT_TIME}s${NC}"
    return 1
}

# Function to check if Docker is running
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        echo -e "${RED}Error: Docker is not running${NC}"
        echo "Please start Docker and try again"
        exit 1
    fi
}

# Function to check if docker-compose is available
check_docker_compose() {
    if ! command -v docker-compose &> /dev/null; then
        echo -e "${RED}Error: docker-compose is not installed${NC}"
        exit 1
    fi
}

# Step 0: Prerequisites
echo -e "${YELLOW}[0/6] Checking prerequisites...${NC}"
check_docker
check_docker_compose
echo -e "${GREEN}✓ Docker and docker-compose are available${NC}"
echo ""

# Step 1: Start Docker Compose services
echo -e "${YELLOW}[1/6] Starting Docker Compose services...${NC}"
cd "$PROJECT_ROOT"

# Check if services are already running
if docker-compose ps | grep -q "Up"; then
    echo -e "${YELLOW}Services are already running. Restarting them...${NC}"
    docker-compose restart
    sleep 5
else
    echo -e "${CYAN}Starting all services...${NC}"
    docker-compose up -d
fi

echo -e "${GREEN}✓ Services started${NC}"
echo ""

# Step 2: Wait for core infrastructure
echo -e "${YELLOW}[2/6] Waiting for core infrastructure...${NC}"

echo -e "${CYAN}Waiting for PostgreSQL to be ready...${NC}"
MAX_PG_WAIT=60
PG_WAIT=0
while [ $PG_WAIT -lt $MAX_PG_WAIT ]; do
    if docker exec media-db pg_isready -U admin > /dev/null 2>&1; then
        echo -e "${GREEN}✓ PostgreSQL is ready${NC}"
        break
    fi
    echo -ne "${YELLOW}  Waiting for PostgreSQL... ${PG_WAIT}s${NC}\r"
    sleep 2
    PG_WAIT=$((PG_WAIT + 2))
done

if [ $PG_WAIT -ge $MAX_PG_WAIT ]; then
    echo -e "${RED}✗ PostgreSQL failed to start${NC}"
    exit 1
fi

echo -e "${CYAN}Waiting for Kafka to be ready...${NC}"
sleep 10  # Kafka takes a bit longer to start
echo -e "${GREEN}✓ Kafka should be ready${NC}"
echo ""

# Step 3: Wait for microservices
echo -e "${YELLOW}[3/6] Waiting for microservices to be healthy...${NC}"
echo -e "${CYAN}This may take 1-2 minutes as services build and initialize...${NC}"
echo ""

# Give services some time to start
sleep 20

# Check each service (non-blocking - we'll report status but continue)
SERVICES_OK=true

wait_for_service "User Service" "http://localhost:8084/actuator/health" || SERVICES_OK=false
wait_for_service "Catalog Service" "http://localhost:8081/actuator/health" || SERVICES_OK=false
wait_for_service "Engagement Service" "http://localhost:8083/actuator/health" || SERVICES_OK=false
wait_for_service "Recommendation Service" "http://localhost:8085/actuator/health" || SERVICES_OK=false
wait_for_service "ML Service" "http://localhost:5000/health" || SERVICES_OK=false

echo ""

if [ "$SERVICES_OK" = false ]; then
    echo -e "${YELLOW}⚠ Warning: Some services are not healthy${NC}"
    echo -e "${YELLOW}The test will continue but may fail. Check logs with: docker-compose logs [service-name]${NC}"
    echo ""
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${RED}Aborting test${NC}"
        exit 1
    fi
fi

# Step 4: Seed test data
echo -e "${YELLOW}[4/6] Seeding test data...${NC}"

if [ -f "$SEED_SCRIPT" ]; then
    echo -e "${CYAN}Running seed script...${NC}"
    
    # Check .env file for the actual PostgreSQL user
    if [ -f "$PROJECT_ROOT/.env" ]; then
        PG_USER=$(grep "^POSTGRES_USER=" "$PROJECT_ROOT/.env" | cut -d'=' -f2)
        if [ -z "$PG_USER" ]; then
            PG_USER="admin"
        fi
    else
        PG_USER="admin"
    fi
    
    echo -e "${CYAN}Using PostgreSQL user: $PG_USER${NC}"
    
    if docker exec -i media-db psql -U "$PG_USER" < "$SEED_SCRIPT" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Test data seeded successfully${NC}"
    else
        echo -e "${YELLOW}⚠ Warning: Seed script failed (data may already exist)${NC}"
        echo -e "${YELLOW}If this is the first run, try manually:${NC}"
        echo -e "${YELLOW}  docker exec -i media-db psql -U $PG_USER < scripts/seed-e2e-data.sql${NC}"
    fi
else
    echo -e "${YELLOW}⚠ Warning: Seed script not found at $SEED_SCRIPT${NC}"
    echo -e "${YELLOW}The test will try to create data dynamically${NC}"
fi

echo ""

# Step 5: Install Python dependencies if needed
echo -e "${YELLOW}[5/6] Checking Python dependencies...${NC}"

if ! python3 -c "import requests" 2>/dev/null; then
    echo -e "${CYAN}Installing required Python packages...${NC}"
    pip3 install requests --quiet
    echo -e "${GREEN}✓ Dependencies installed${NC}"
else
    echo -e "${GREEN}✓ Python dependencies OK${NC}"
fi

echo ""

# Step 6: Run E2E test
echo -e "${YELLOW}[6/6] Running E2E test...${NC}"
echo ""

if [ ! -f "$E2E_TEST" ]; then
    echo -e "${RED}Error: E2E test script not found at $E2E_TEST${NC}"
    exit 1
fi

# Make script executable
chmod +x "$E2E_TEST"

# Run the test
python3 "$E2E_TEST"
TEST_EXIT_CODE=$?

echo ""

# Summary
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Summary${NC}"
echo -e "${BLUE}========================================${NC}"

if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✓ E2E TEST PASSED${NC}"
else
    echo -e "${RED}✗ E2E TEST FAILED${NC}"
    echo ""
    echo -e "${YELLOW}Troubleshooting Tips:${NC}"
    echo "1. Check service logs: docker-compose logs [service-name]"
    echo "2. Check service status: docker-compose ps"
    echo "3. Verify database: docker exec -it media-db psql -U admin -d catalog_db -c 'SELECT COUNT(*) FROM media;'"
    echo "4. Check if seed data was loaded: docker exec -it media-db psql -U admin -d catalog_db -c \"SELECT title FROM media WHERE id LIKE 'media-action%';\""
    echo "5. Restart services: docker-compose down && docker-compose up -d"
fi

echo ""
echo -e "${CYAN}Services are still running.${NC}"
echo -e "${CYAN}To stop them: docker-compose down${NC}"
echo -e "${CYAN}To view logs: docker-compose logs -f [service-name]${NC}"
echo ""

exit $TEST_EXIT_CODE
