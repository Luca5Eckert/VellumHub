# E2E Test Quick Reference

## Quick Start

```bash
# Run E2E test (automated)
./scripts/run_e2e_test.sh
```

## Manual Execution

```bash
# 1. Start services
docker-compose up -d

# 2. Wait for services to be ready (1-2 minutes)
sleep 60

# 3. Run test
python3 scripts/e2e_test.py

# 4. View results
# Check test output for pass/fail status

# 5. Optional: View service logs
docker-compose logs -f recommendation-service
docker-compose logs -f ml-service
```

## Test Scenarios

The E2E test covers:

✅ User Registration (`POST /auth/register`)
✅ User Login (`POST /auth/login`)
✅ Media Creation (`POST /media`) - 10 items
✅ Engagement Tracking (`POST /engagement`) - 5 interactions
✅ Kafka Event Processing
✅ Recommendation Generation (`GET /api/recommendations`)
✅ Recommendation Validation

## Expected Results

- **Status**: All steps should PASS
- **Time**: < 30 seconds total
- **Recommendations**: Should favor ACTION genre over THRILLER
- **HTTP Codes**: 
  - Register: 201 (first time) or 409 (already exists)
  - Login: 200
  - Create Media: 201
  - Create Engagement: 201
  - Get Recommendations: 200

## Troubleshooting

### Services not starting
```bash
docker-compose logs [service-name]
docker-compose restart [service-name]
```

### Test fails
```bash
# Check individual service health
curl http://localhost:8084/actuator/health  # User
curl http://localhost:8081/actuator/health  # Catalog
curl http://localhost:8083/actuator/health  # Engagement
curl http://localhost:8085/actuator/health  # Recommendation
curl http://localhost:5000/health           # ML
```

### Clean restart
```bash
docker-compose down -v
docker-compose up -d
sleep 60
python3 scripts/e2e_test.py
```

## Documentation

Full documentation: [docs/E2E_TEST_GUIDE.md](../docs/E2E_TEST_GUIDE.md)
