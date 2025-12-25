#!/bin/bash
#
# Wait for all services to be healthy
# Used by CI/CD pipelines before running E2E tests
#
set -euo pipefail

# Configuration
readonly BASE_URL="${BASE_URL:-http://localhost}"
readonly USER_SERVICE_PORT="${USER_SERVICE_PORT:-8084}"
readonly CATALOG_SERVICE_PORT="${CATALOG_SERVICE_PORT:-8081}"
readonly ENGAGEMENT_SERVICE_PORT="${ENGAGEMENT_SERVICE_PORT:-8083}"
readonly RECOMMENDATION_SERVICE_PORT="${RECOMMENDATION_SERVICE_PORT:-8085}"
readonly ML_SERVICE_PORT="${ML_SERVICE_PORT:-5000}"
readonly MAX_RETRIES="${MAX_RETRIES:-60}"
readonly RETRY_DELAY="${RETRY_DELAY:-5}"

# Colors
readonly GREEN='\033[0;32m'
readonly RED='\033[0;31m'
readonly YELLOW='\033[1;33m'
readonly NC='\033[0m'

log_info() { echo -e "${YELLOW}[INFO]${NC} $*"; }
log_success() { echo -e "${GREEN}[✓]${NC} $*"; }
log_error() { echo -e "${RED}[✗]${NC} $*" >&2; }

wait_for_service() {
    local service_name=$1
    local health_url=$2
    local attempt=1
    
    log_info "Waiting for $service_name..."
    
    while [[ $attempt -le $MAX_RETRIES ]]; do
        if curl -sf "$health_url" >/dev/null 2>&1; then
            log_success "$service_name is ready!"
            return 0
        fi
        
        echo "  Attempt $attempt/$MAX_RETRIES: $service_name not ready, retrying in ${RETRY_DELAY}s..."
        sleep "$RETRY_DELAY"
        ((attempt++))
    done
    
    log_error "$service_name failed to become healthy after $MAX_RETRIES attempts"
    return 1
}

main() {
    echo ""
    echo "=================================="
    echo "Waiting for all services to start"
    echo "=================================="
    echo ""
    
    local failed=0
    
    wait_for_service "User Service" "$BASE_URL:$USER_SERVICE_PORT/actuator/health" || failed=1
    wait_for_service "Catalog Service" "$BASE_URL:$CATALOG_SERVICE_PORT/actuator/health" || failed=1
    wait_for_service "Engagement Service" "$BASE_URL:$ENGAGEMENT_SERVICE_PORT/actuator/health" || failed=1
    wait_for_service "Recommendation Service" "$BASE_URL:$RECOMMENDATION_SERVICE_PORT/actuator/health" || failed=1
    wait_for_service "ML Service" "$BASE_URL:$ML_SERVICE_PORT/health" || failed=1
    
    echo ""
    
    if [[ $failed -eq 0 ]]; then
        log_success "All services are healthy and ready!"
        exit 0
    else
        log_error "Some services failed to start"
        exit 1
    fi
}

main "$@"
