# Service Communication Verification Guide

## Overview

This document describes how to verify communication between VellumHub microservices. The system uses both **synchronous REST communication** (via Feign Client) and **asynchronous event-driven communication** (via Apache Kafka).

## Communication Architecture

### Synchronous Communication (REST/Feign)

**Recommendation Service → Catalog Service**
- **Purpose:** Bulk fetch book details to enrich recommendations
- **Endpoint:** `POST /api/book/bulk`
- **Protocol:** HTTP REST via Spring Cloud OpenFeign
- **Configuration:** Load-balanced via Ribbon

### Asynchronous Communication (Kafka Events)

#### Catalog Service → Recommendation Service
- **Events Published:**
  - `created-book` - When a new book is added to the catalog
  - `updated-book` - When book details are modified
  - `deleted-book` - When a book is removed
- **Purpose:** Keep recommendation service book features synchronized

#### Engagement Service → Recommendation Service
- **Events Published:**
  - `created-rating` - When a user rates a book
- **Purpose:** Update user profiles for personalized recommendations

## Verification Methods

### 1. Automated Testing Script

Run the comprehensive communication test script:

```bash
./scripts/test-service-communication.sh
```

**What it checks:**
- ✓ All service health endpoints
- ✓ Kafka connectivity for each service
- ✓ Kafka topics existence
- ✓ Consumer group status and lag
- ✓ Docker network connectivity
- ✓ Feign client endpoints availability

**Expected Output:**
```
=========================================
VellumHub Service Communication Test
=========================================

Testing User Service Health...
✓ User Service: Service is UP and healthy
✓ User Service Kafka: Kafka connection is UP

Testing Catalog Service Health...
✓ Catalog Service: Service is UP and healthy
✓ Catalog Service Kafka: Kafka connection is UP

[... more tests ...]

=========================================
Test Summary
=========================================
Passed: 15
Warnings: 2
Failed: 0

All tests passed! ✓
```

### 2. Manual Health Check

Check each service health endpoint:

```bash
# User Service
curl http://localhost:8084/actuator/health | jq .

# Catalog Service
curl http://localhost:8081/actuator/health | jq .

# Engagement Service
curl http://localhost:8083/actuator/health | jq .

# Recommendation Service
curl http://localhost:8085/actuator/health | jq .
```

**Expected Response:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    },
    "kafka": {
      "status": "UP",
      "details": {
        "clusterId": "...",
        "brokerId": 1
      }
    }
  }
}
```

### 3. Kafka Communication Verification

#### Check Kafka Topics

```bash
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

**Expected Topics:**
- `created-book`
- `updated-book`
- `deleted-book`
- `created-rating`

#### Monitor Consumer Groups

```bash
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group recommendation-service-group
```

**What to look for:**
- Consumer group should be active
- Lag should be 0 or low (< 100 messages)
- All partitions should have active consumers

#### View Recent Messages

```bash
# View last 5 messages from created-book topic
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic created-book \
  --from-beginning \
  --max-messages 5
```

### 4. Feign Client Communication Test

#### Test Catalog Bulk Endpoint

```bash
# This endpoint is used by the Recommendation Service Feign client
curl -X POST http://localhost:8081/api/book/bulk \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '["uuid1", "uuid2"]'
```

**Note:** You need a valid JWT token. Get one by logging in through the User Service.

#### Check Network Connectivity (Docker)

```bash
# From recommendation-service container, ping catalog-service
docker exec recommendation-service ping -c 3 catalog-service
```

### 5. Kafka UI Monitoring

Access Kafka UI at http://localhost:8090 to visually monitor:

1. **Brokers Tab**
   - Verify broker is UP
   - Check broker metrics

2. **Topics Tab**
   - List all topics
   - View messages per topic
   - Check partition distribution

3. **Consumers Tab**
   - View consumer groups
   - Monitor consumer lag
   - Check active consumers

## End-to-End Communication Flow Test

### Scenario 1: Book Creation Flow

1. **Create a Book** (Catalog Service)
   ```bash
   curl -X POST http://localhost:8081/api/book \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $TOKEN" \
     -d '{
       "title": "Test Book",
       "author": "Test Author",
       "genres": ["FICTION"]
     }'
   ```

2. **Verify Kafka Event**
   - Check Kafka UI for new message in `created-book` topic
   - Or monitor logs:
   ```bash
   docker logs catalog-service | grep "Event sent successfully"
   ```

3. **Verify Consumer Processed Event**
   ```bash
   docker logs recommendation-service | grep "Kafka.*created-book"
   ```

4. **Verify Book Feature Created**
   - Check recommendation-service database
   - Or check logs for book feature creation

### Scenario 2: Rating Creation Flow

1. **Create a Rating** (Engagement Service)
   ```bash
   curl -X POST http://localhost:8083/api/rating \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $TOKEN" \
     -d '{
       "bookId": "uuid",
       "rating": 5
     }'
   ```

2. **Verify Event Published**
   ```bash
   docker logs engagement-service | grep "created-rating"
   ```

3. **Verify Event Consumed**
   ```bash
   docker logs recommendation-service | grep "created-rating"
   ```

### Scenario 3: Recommendation Fetch (Feign Client)

1. **Request Recommendations** (triggers Feign call)
   ```bash
   curl http://localhost:8085/api/recommendation \
     -H "Authorization: Bearer $TOKEN"
   ```

2. **Verify Feign Client Call**
   ```bash
   # Check recommendation-service logs
   docker logs recommendation-service | grep -i feign
   
   # Check catalog-service access logs
   docker logs catalog-service | grep "/api/book/bulk"
   ```

3. **Expected Flow:**
   - Recommendation Service computes similar book IDs
   - Makes POST to Catalog Service `/api/book/bulk` with IDs
   - Receives full book details
   - Returns enriched recommendations

## Troubleshooting Communication Issues

### Issue: Service Health Shows Kafka DOWN

**Symptoms:**
```json
"kafka": {
  "status": "DOWN",
  "details": {
    "error": "..."
  }
}
```

**Solutions:**
1. Check Kafka container is running: `docker ps | grep kafka`
2. Verify Kafka bootstrap servers configuration
3. Check network connectivity: `docker exec SERVICE_NAME nc -z kafka 29092`
4. Review Kafka logs: `docker logs kafka`

### Issue: Consumer Group Not Consuming

**Symptoms:**
- High consumer lag
- No log messages about consumed events
- Consumer group not visible in Kafka UI

**Solutions:**
1. Verify consumer is running: `docker ps | grep recommendation-service`
2. Check for consumer errors in logs: `docker logs recommendation-service | grep -i error`
3. Verify topic exists: `docker exec kafka kafka-topics --list --bootstrap-server localhost:9092`
4. Reset consumer offset if needed (development only)

### Issue: Feign Client Cannot Reach Catalog Service

**Symptoms:**
```
feign.RetryableException: Connection refused
```

**Solutions:**
1. Verify both services are running: `docker ps`
2. Check they're on the same network: `docker network inspect mrs-network`
3. Verify DNS resolution: `docker exec recommendation-service ping catalog-service`
4. Check Feign configuration in `application.properties`
5. Review Ribbon/LoadBalancer configuration

### Issue: No Messages in Kafka Topics

**Symptoms:**
- Topics exist but are empty
- No events visible in Kafka UI

**Possible Causes:**
1. Producers haven't published any events yet (normal for new installation)
2. Topics were created manually but no operations triggered
3. Producer errors prevented message publishing

**Solutions:**
1. Trigger an operation (create a book, create a rating)
2. Check producer logs for errors
3. Verify producer configuration (bootstrap servers, serializers)

## Configuration Reference

### Feign Client Configuration

**File:** `recommendation-service/src/main/resources/application.properties`

```properties
# Connection timeouts
feign.client.config.default.connectTimeout=5000
feign.client.config.default.readTimeout=5000

# Service discovery/load balancer
catalog-service.ribbon.listOfServers=http://catalog-service:8080
```

### Kafka Producer Configuration

**Services:** catalog-service, engagement-service

```properties
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS}
```

### Kafka Consumer Configuration

**Service:** recommendation-service

```properties
spring.kafka.consumer.group-id=recommendation-service-group
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS}
```

## Performance Metrics

### Key Metrics to Monitor

1. **Kafka Producer Metrics** (catalog-service, engagement-service)
   - `kafka.producer.record-send-rate` - Messages sent per second
   - `kafka.producer.record-error-rate` - Failed sends per second
   - `kafka.producer.request-latency-avg` - Average latency

2. **Kafka Consumer Metrics** (recommendation-service)
   - `kafka.consumer.records-consumed-rate` - Messages consumed per second
   - `kafka.consumer.fetch-latency-avg` - Average fetch latency
   - `kafka.consumer.records-lag-max` - Maximum lag

3. **Feign Client Metrics** (recommendation-service)
   - Request count
   - Error rate
   - Response time

Access metrics at: `http://localhost:808X/actuator/metrics`

## Continuous Monitoring

### Recommended Checks

1. **Daily**
   - Run `./scripts/test-service-communication.sh`
   - Check Kafka UI for consumer lag
   - Review error logs

2. **Weekly**
   - Verify all end-to-end flows
   - Check performance metrics trends
   - Review Kafka topic retention

3. **On Deployment**
   - Run full communication test
   - Verify all services healthy
   - Check consumer groups reset properly

## Related Documentation

- [Kafka Monitoring Guide](KAFKA_MONITORING.md)
- [Main README](../README.md)
- [Spring Cloud OpenFeign Documentation](https://spring.io/projects/spring-cloud-openfeign)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
