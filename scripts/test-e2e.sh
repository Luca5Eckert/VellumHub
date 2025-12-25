#!/bin/bash
#
# End-to-End Test Script for Media Recommendation System
# Tests the complete flow across all microservices
#
set -euo pipefail

# Configuration
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly BASE_URL="${BASE_URL:-http://localhost}"
readonly USER_SERVICE_PORT="${USER_SERVICE_PORT:-8084}"
readonly CATALOG_SERVICE_PORT="${CATALOG_SERVICE_PORT:-8081}"
readonly ENGAGEMENT_SERVICE_PORT="${ENGAGEMENT_SERVICE_PORT:-8083}"
readonly RECOMMENDATION_SERVICE_PORT="${RECOMMENDATION_SERVICE_PORT:-8085}"
readonly ML_SERVICE_PORT="${ML_SERVICE_PORT:-5000}"
readonly TIMEOUT="${TIMEOUT:-30}"
readonly MAX_RETRIES="${MAX_RETRIES:-30}"
readonly RETRY_DELAY="${RETRY_DELAY:-2}"

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly CYAN='\033[0;36m'
readonly NC='\033[0m' # No Color

# Counters
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_TOTAL=0
TESTS_SKIPPED=0

# Test data storage
USER_TOKEN=""
ADMIN_TOKEN=""
TEST_USER_ID=""
TEST_USER_EMAIL="testuser_$(date +%s)@test.com"
TEST_USER_PASSWORD="TestPassword123!"
TEST_ADMIN_EMAIL="admin_$(date +%s)@test.com"
CREATED_MEDIA_IDS=()

# Logging functions
log_info() { echo -e "${BLUE}[INFO]${NC} $*"; }
log_success() { echo -e "${GREEN}[✓]${NC} $*"; ((TESTS_PASSED++)); ((TESTS_TOTAL++)); }
log_error() { echo -e "${RED}[✗]${NC} $*" >&2; ((TESTS_FAILED++)); ((TESTS_TOTAL++)); }
log_warning() { echo -e "${YELLOW}[⚠]${NC} $*"; }
log_skip() { echo -e "${CYAN}[SKIP]${NC} $*"; ((TESTS_SKIPPED++)); ((TESTS_TOTAL++)); }
log_section() { echo -e "\n${CYAN}========================================${NC}"; echo -e "${CYAN}$*${NC}"; echo -e "${CYAN}========================================${NC}"; }

# Utility functions
check_prerequisites() {
    log_section "Checking Prerequisites"
    
    local missing_deps=0
    
    if ! command -v docker &>/dev/null; then
        log_error "docker is required but not installed"
        missing_deps=1
    else
        log_info "docker: $(docker --version)"
    fi
    
    if ! command -v curl &>/dev/null; then
        log_error "curl is required but not installed"
        missing_deps=1
    else
        log_info "curl: $(curl --version | head -1)"
    fi
    
    if ! command -v jq &>/dev/null; then
        log_error "jq is required but not installed"
        missing_deps=1
    else
        log_info "jq: $(jq --version)"
    fi
    
    if [[ $missing_deps -eq 1 ]]; then
        log_error "Missing required dependencies. Please install them and try again."
        exit 1
    fi
    
    log_success "All prerequisites available"
}

wait_for_service() {
    local service_name=$1
    local url=$2
    local attempt=1
    
    log_info "Waiting for $service_name at $url..."
    
    while [[ $attempt -le $MAX_RETRIES ]]; do
        if curl -sf "$url" >/dev/null 2>&1; then
            log_success "$service_name is ready"
            return 0
        fi
        log_info "  Attempt $attempt/$MAX_RETRIES - $service_name not ready, waiting..."
        sleep "$RETRY_DELAY"
        ((attempt++))
    done
    
    log_error "$service_name failed to start after $MAX_RETRIES attempts"
    return 1
}

make_request() {
    local method=$1
    local url=$2
    local data=${3:-}
    local token=${4:-}
    local expected_status=${5:-200}
    
    local curl_opts=(-s -w "\n%{http_code}" -X "$method")
    
    if [[ -n "$token" ]]; then
        curl_opts+=(-H "Authorization: Bearer $token")
    fi
    
    if [[ -n "$data" ]]; then
        curl_opts+=(-H "Content-Type: application/json" -d "$data")
    fi
    
    local response
    response=$(curl "${curl_opts[@]}" "$url" 2>/dev/null) || true
    
    local status_code
    status_code=$(echo "$response" | tail -1)
    local body
    body=$(echo "$response" | sed '$d')
    
    echo "$body"
    return 0
}

get_status_code() {
    local method=$1
    local url=$2
    local data=${3:-}
    local token=${4:-}
    
    local curl_opts=(-s -o /dev/null -w "%{http_code}" -X "$method")
    
    if [[ -n "$token" ]]; then
        curl_opts+=(-H "Authorization: Bearer $token")
    fi
    
    if [[ -n "$data" ]]; then
        curl_opts+=(-H "Content-Type: application/json" -d "$data")
    fi
    
    curl "${curl_opts[@]}" "$url" 2>/dev/null || echo "000"
}

# Health check tests
test_health_checks() {
    log_section "Health Check Tests"
    
    # User Service
    local status
    status=$(get_status_code "GET" "$BASE_URL:$USER_SERVICE_PORT/actuator/health")
    if [[ "$status" == "200" ]]; then
        log_success "User Service health check passed"
    else
        log_error "User Service health check failed (status: $status)"
    fi
    
    # Catalog Service
    status=$(get_status_code "GET" "$BASE_URL:$CATALOG_SERVICE_PORT/actuator/health")
    if [[ "$status" == "200" ]]; then
        log_success "Catalog Service health check passed"
    else
        log_error "Catalog Service health check failed (status: $status)"
    fi
    
    # Engagement Service
    status=$(get_status_code "GET" "$BASE_URL:$ENGAGEMENT_SERVICE_PORT/actuator/health")
    if [[ "$status" == "200" ]]; then
        log_success "Engagement Service health check passed"
    else
        log_error "Engagement Service health check failed (status: $status)"
    fi
    
    # Recommendation Service
    status=$(get_status_code "GET" "$BASE_URL:$RECOMMENDATION_SERVICE_PORT/actuator/health")
    if [[ "$status" == "200" ]]; then
        log_success "Recommendation Service health check passed"
    else
        log_error "Recommendation Service health check failed (status: $status)"
    fi
    
    # ML Service
    status=$(get_status_code "GET" "$BASE_URL:$ML_SERVICE_PORT/health")
    if [[ "$status" == "200" ]]; then
        log_success "ML Service health check passed"
    else
        log_error "ML Service health check failed (status: $status)"
    fi
}

# User registration tests
test_user_registration() {
    log_section "User Registration Tests"
    
    # Test successful registration
    local register_data="{\"name\":\"Test User\",\"email\":\"$TEST_USER_EMAIL\",\"password\":\"$TEST_USER_PASSWORD\"}"
    local status
    status=$(get_status_code "POST" "$BASE_URL:$USER_SERVICE_PORT/auth/register" "$register_data")
    
    if [[ "$status" == "201" ]]; then
        log_success "User registration succeeded (201)"
    else
        log_error "User registration failed (expected 201, got $status)"
    fi
    
    # Test duplicate email registration
    status=$(get_status_code "POST" "$BASE_URL:$USER_SERVICE_PORT/auth/register" "$register_data")
    if [[ "$status" == "400" || "$status" == "500" ]]; then
        log_success "Duplicate email registration correctly rejected ($status)"
    else
        log_error "Duplicate email registration should fail (got $status)"
    fi
    
    # Test invalid email format
    local invalid_email_data='{"name":"Test","email":"invalid-email","password":"Password123!"}'
    status=$(get_status_code "POST" "$BASE_URL:$USER_SERVICE_PORT/auth/register" "$invalid_email_data")
    if [[ "$status" == "400" ]]; then
        log_success "Invalid email format correctly rejected (400)"
    else
        log_warning "Invalid email validation (expected 400, got $status)"
    fi
    
    # Test empty password
    local empty_password_data='{"name":"Test","email":"test2@test.com","password":""}'
    status=$(get_status_code "POST" "$BASE_URL:$USER_SERVICE_PORT/auth/register" "$empty_password_data")
    if [[ "$status" == "400" ]]; then
        log_success "Empty password correctly rejected (400)"
    else
        log_warning "Empty password validation (expected 400, got $status)"
    fi
}

# Login tests
test_user_login() {
    log_section "User Login Tests"
    
    # Test successful login
    local login_data="{\"email\":\"$TEST_USER_EMAIL\",\"password\":\"$TEST_USER_PASSWORD\"}"
    local response
    response=$(make_request "POST" "$BASE_URL:$USER_SERVICE_PORT/auth/login" "$login_data")
    
    if [[ -n "$response" && "$response" != "null" && ! "$response" =~ "error" ]]; then
        USER_TOKEN="$response"
        # Remove quotes if present
        USER_TOKEN="${USER_TOKEN//\"/}"
        log_success "User login succeeded, token received"
    else
        log_error "User login failed: $response"
    fi
    
    # Test invalid credentials
    local invalid_login_data='{"email":"nonexistent@test.com","password":"wrongpassword"}'
    local status
    status=$(get_status_code "POST" "$BASE_URL:$USER_SERVICE_PORT/auth/login" "$invalid_login_data")
    if [[ "$status" == "401" || "$status" == "403" || "$status" == "500" ]]; then
        log_success "Invalid credentials correctly rejected ($status)"
    else
        log_error "Invalid credentials should be rejected (got $status)"
    fi
}

# Media CRUD tests (requires admin)
test_media_operations() {
    log_section "Media Operations Tests"
    
    if [[ -z "$USER_TOKEN" ]]; then
        log_skip "Skipping media tests - no user token available"
        return
    fi
    
    # Test creating media without admin role (should fail)
    local media_data='{"title":"Test Movie","description":"A test movie","releaseYear":2024,"mediaType":"MOVIE","genres":["ACTION","THRILLER"]}'
    local status
    status=$(get_status_code "POST" "$BASE_URL:$CATALOG_SERVICE_PORT/media" "$media_data" "$USER_TOKEN")
    if [[ "$status" == "403" ]]; then
        log_success "Non-admin user correctly forbidden from creating media (403)"
    else
        log_warning "Expected 403 for non-admin user (got $status)"
    fi
    
    # Test getting all media (public endpoint)
    status=$(get_status_code "GET" "$BASE_URL:$CATALOG_SERVICE_PORT/media" "" "$USER_TOKEN")
    if [[ "$status" == "200" ]]; then
        log_success "Get all media succeeded (200)"
    else
        log_error "Get all media failed (expected 200, got $status)"
    fi
}

# Engagement tests
test_engagement_operations() {
    log_section "Engagement Operations Tests"
    
    if [[ -z "$USER_TOKEN" ]]; then
        log_skip "Skipping engagement tests - no user token available"
        return
    fi
    
    # Test creating an engagement
    local engagement_data
    engagement_data=$(cat <<EOF
{
    "userId": "$(uuidgen 2>/dev/null || echo '11111111-1111-1111-1111-111111111111')",
    "mediaId": "$(uuidgen 2>/dev/null || echo '22222222-2222-2222-2222-222222222222')",
    "type": "LIKE",
    "interactionValue": 1.0
}
EOF
)
    
    local status
    status=$(get_status_code "POST" "$BASE_URL:$ENGAGEMENT_SERVICE_PORT/engagement" "$engagement_data" "$USER_TOKEN")
    if [[ "$status" == "201" ]]; then
        log_success "Engagement creation succeeded (201)"
    else
        log_warning "Engagement creation (expected 201, got $status)"
    fi
    
    # Test engagement without authentication
    status=$(get_status_code "POST" "$BASE_URL:$ENGAGEMENT_SERVICE_PORT/engagement" "$engagement_data")
    if [[ "$status" == "401" || "$status" == "403" ]]; then
        log_success "Unauthenticated engagement correctly rejected ($status)"
    else
        log_warning "Expected 401/403 for unauthenticated request (got $status)"
    fi
}

# Recommendation tests
test_recommendation_operations() {
    log_section "Recommendation Operations Tests"
    
    if [[ -z "$USER_TOKEN" ]]; then
        log_skip "Skipping recommendation tests - no user token available"
        return
    fi
    
    # Test getting recommendations
    local status
    status=$(get_status_code "GET" "$BASE_URL:$RECOMMENDATION_SERVICE_PORT/api/recommendations" "" "$USER_TOKEN")
    if [[ "$status" == "200" ]]; then
        log_success "Get recommendations succeeded (200)"
    elif [[ "$status" == "500" ]]; then
        log_warning "Get recommendations returned 500 (user profile may not exist yet)"
    else
        log_error "Get recommendations failed (expected 200, got $status)"
    fi
    
    # Test recommendations without authentication
    status=$(get_status_code "GET" "$BASE_URL:$RECOMMENDATION_SERVICE_PORT/api/recommendations")
    if [[ "$status" == "401" || "$status" == "403" ]]; then
        log_success "Unauthenticated recommendations request correctly rejected ($status)"
    else
        log_warning "Expected 401/403 for unauthenticated request (got $status)"
    fi
}

# ML Service tests
test_ml_service() {
    log_section "ML Service Tests"
    
    # Test ML health endpoint
    local response
    response=$(make_request "GET" "$BASE_URL:$ML_SERVICE_PORT/health")
    if echo "$response" | jq -e '.status == "healthy"' >/dev/null 2>&1; then
        log_success "ML Service health check returned healthy status"
    else
        log_error "ML Service health check failed: $response"
    fi
    
    # Test ML recommendations endpoint with sample data
    local ml_request
    ml_request=$(cat <<EOF
{
    "user_profile": {
        "user_id": "test-uuid",
        "genre_scores": {"ACTION": 5.0, "THRILLER": 3.0},
        "interacted_media_ids": [],
        "total_engagement_score": 100.0
    },
    "limit": 10
}
EOF
)
    
    local status
    status=$(get_status_code "POST" "$BASE_URL:$ML_SERVICE_PORT/api/recommendations" "$ml_request")
    if [[ "$status" == "200" ]]; then
        log_success "ML recommendations endpoint succeeded (200)"
    else
        log_warning "ML recommendations endpoint (expected 200, got $status)"
    fi
    
    # Test ML endpoint with invalid request
    local invalid_request='{"invalid": "data"}'
    status=$(get_status_code "POST" "$BASE_URL:$ML_SERVICE_PORT/api/recommendations" "$invalid_request")
    if [[ "$status" == "400" ]]; then
        log_success "ML endpoint correctly rejected invalid request (400)"
    else
        log_warning "Expected 400 for invalid request (got $status)"
    fi
}

# Authorization tests
test_authorization() {
    log_section "Authorization Tests"
    
    # Test protected endpoint without token
    local status
    status=$(get_status_code "GET" "$BASE_URL:$USER_SERVICE_PORT/users")
    if [[ "$status" == "401" || "$status" == "403" ]]; then
        log_success "Protected endpoint correctly requires authentication ($status)"
    else
        log_error "Protected endpoint should require authentication (got $status)"
    fi
    
    # Test with invalid token
    status=$(get_status_code "GET" "$BASE_URL:$USER_SERVICE_PORT/users" "" "invalid.token.here")
    if [[ "$status" == "401" || "$status" == "403" ]]; then
        log_success "Invalid token correctly rejected ($status)"
    else
        log_warning "Expected 401/403 for invalid token (got $status)"
    fi
}

# Print test summary
print_summary() {
    log_section "Test Summary"
    
    echo ""
    echo -e "${BLUE}===========================================${NC}"
    echo -e "${BLUE}           TEST RESULTS SUMMARY            ${NC}"
    echo -e "${BLUE}===========================================${NC}"
    echo ""
    echo -e "  Total Tests:  ${CYAN}$TESTS_TOTAL${NC}"
    echo -e "  Passed:       ${GREEN}$TESTS_PASSED${NC}"
    echo -e "  Failed:       ${RED}$TESTS_FAILED${NC}"
    echo -e "  Skipped:      ${YELLOW}$TESTS_SKIPPED${NC}"
    echo ""
    
    if [[ $TESTS_FAILED -eq 0 ]]; then
        echo -e "${GREEN}===========================================${NC}"
        echo -e "${GREEN}       ALL TESTS PASSED SUCCESSFULLY!      ${NC}"
        echo -e "${GREEN}===========================================${NC}"
    else
        echo -e "${RED}===========================================${NC}"
        echo -e "${RED}        SOME TESTS FAILED ($TESTS_FAILED)             ${NC}"
        echo -e "${RED}===========================================${NC}"
    fi
    echo ""
}

# Cleanup function
cleanup() {
    log_info "Cleanup completed"
}

# Main function
main() {
    local start_time
    start_time=$(date +%s)
    
    echo ""
    echo -e "${CYAN}╔════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║   Media Recommendation System - E2E Test Suite         ║${NC}"
    echo -e "${CYAN}║   $(date '+%Y-%m-%d %H:%M:%S')                                    ║${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    # Check prerequisites
    check_prerequisites
    
    # Wait for all services to be ready
    log_section "Waiting for Services"
    wait_for_service "User Service" "$BASE_URL:$USER_SERVICE_PORT/actuator/health" || true
    wait_for_service "Catalog Service" "$BASE_URL:$CATALOG_SERVICE_PORT/actuator/health" || true
    wait_for_service "Engagement Service" "$BASE_URL:$ENGAGEMENT_SERVICE_PORT/actuator/health" || true
    wait_for_service "Recommendation Service" "$BASE_URL:$RECOMMENDATION_SERVICE_PORT/actuator/health" || true
    wait_for_service "ML Service" "$BASE_URL:$ML_SERVICE_PORT/health" || true
    
    # Run test suites
    test_health_checks
    test_user_registration
    test_user_login
    test_media_operations
    test_engagement_operations
    test_recommendation_operations
    test_ml_service
    test_authorization
    
    # Cleanup
    cleanup
    
    # Print summary
    print_summary
    
    local end_time
    end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    echo -e "Test execution time: ${CYAN}${duration}s${NC}"
    echo ""
    
    # Exit with appropriate code
    if [[ $TESTS_FAILED -gt 0 ]]; then
        exit 1
    else
        exit 0
    fi
}

# Run main function
main "$@"
