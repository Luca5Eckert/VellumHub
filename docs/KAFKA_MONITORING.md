# Kafka Monitoring Guide

## Overview

This document describes the Kafka monitoring setup for VellumHub. The system includes multiple monitoring approaches to ensure Kafka communication is healthy and to provide visibility into message flow across microservices.

## Monitoring Tools

### 1. Kafka UI (Provectus)

**Access:** http://localhost:8090

Kafka UI is a web-based management and monitoring tool that provides:
- Real-time broker status and configuration
- Topic management and visualization
- Consumer group monitoring with lag metrics
- Message browsing and publishing
- Cluster health overview

**Features:**
- View all Kafka topics and their configurations
- Monitor consumer groups and their offsets
- Browse messages in topics
- Monitor partition distribution
- View broker metrics via JMX

**Usage:**
1. Start the stack: `docker-compose up -d`
2. Navigate to http://localhost:8090
3. The default cluster "vellumhub-cluster" will be displayed
4. Explore topics, consumers, and brokers from the dashboard

### 2. Spring Boot Actuator Health Checks

**Access:** 
- Catalog Service: http://localhost:8081/actuator/health
- Engagement Service: http://localhost:8083/actuator/health
- User Service: http://localhost:8084/actuator/health
- Recommendation Service: http://localhost:8085/actuator/health

Each service exposes health endpoints that include Kafka connectivity status.

**Health Check Response Example:**
```json
{
  "status": "UP",
  "components": {
    "kafka": {
      "status": "UP",
      "details": {
        "clusterId": "...",
        "brokerId": 1
      }
    },
    "db": {
      "status": "UP"
    }
  }
}
```

**Additional Actuator Endpoints:**
- `/actuator/metrics` - Kafka producer/consumer metrics
- `/actuator/prometheus` - Prometheus-formatted metrics
- `/actuator/info` - Application information

### 3. Kafka Health Check Script

**Location:** `scripts/kafka-health-check.sh`

A shell script for quick verification of Kafka status from the command line.

**Usage:**
```bash
./scripts/kafka-health-check.sh
```

**What it checks:**
- ✓ Kafka container is running
- ✓ Broker connectivity
- ✓ List of topics
- ✓ Consumer groups
- ✓ Detailed topic information

### 4. JMX Metrics

The Kafka broker exposes JMX metrics on port 9101.

**Configuration:**
- JMX Port: 9101
- JMX Hostname: localhost

**Accessing JMX Metrics:**
You can connect JMX monitoring tools (like JConsole or VisualVM) to `localhost:9101` to view detailed Kafka metrics.

**Key JMX Metrics:**
- Message throughput (bytes in/out per second)
- Request latency
- Under-replicated partitions
- Active controllers
- Topic and partition metrics

## Kafka Topics

The system uses the following Kafka topics for event-driven communication:

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `created-book` | Catalog Service | Recommendation Service | Book creation events |
| `updated-book` | Catalog Service | Recommendation Service | Book update events |
| `deleted-book` | Catalog Service | Recommendation Service | Book deletion events |
| `created-rating` | Engagement Service | Recommendation Service | Rating creation events |

## Monitoring Best Practices

### 1. Regular Health Checks

Check service health endpoints regularly:
```bash
# Check all services
curl http://localhost:8081/actuator/health | jq .
curl http://localhost:8083/actuator/health | jq .
curl http://localhost:8084/actuator/health | jq .
curl http://localhost:8085/actuator/health | jq .
```

### 2. Monitor Consumer Lag

Consumer lag indicates how far behind consumers are in processing messages.

**Via Kafka UI:**
1. Navigate to Consumer Groups section
2. Check lag for each partition
3. Monitor lag trends over time

**Via CLI:**
```bash
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group recommendation-service-group
```

### 3. Check Producer Success Rate

Monitor producer logs for successful message delivery:
```bash
# Check engagement-service logs for Kafka events
docker logs engagement-service | grep "Event sent successfully"
```

### 4. Topic Retention and Storage

Monitor topic storage and retention:
- Check disk usage in Kafka UI
- Verify retention policies are appropriate
- Monitor partition distribution

## Troubleshooting

### Kafka Not Starting

1. Check Zookeeper status:
```bash
docker logs zookeeper
```

2. Verify Zookeeper connectivity:
```bash
docker exec kafka nc -z zookeeper 2181
```

3. Check Kafka logs:
```bash
docker logs kafka
```

### Services Can't Connect to Kafka

1. Check service health endpoint
2. Verify Kafka is healthy: `./scripts/kafka-health-check.sh`
3. Check service logs:
```bash
docker logs catalog-service | grep -i kafka
```

4. Verify network connectivity:
```bash
docker exec catalog-service nc -z kafka 29092
```

### Messages Not Being Consumed

1. Check if topics exist:
```bash
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

2. Check consumer group status:
```bash
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group recommendation-service-group
```

3. View messages in topic:
```bash
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic created-book \
  --from-beginning \
  --max-messages 10
```

### High Consumer Lag

1. Check if consumers are running
2. Verify consumer processing time
3. Check for errors in consumer logs
4. Consider scaling consumers if needed

## Metrics to Monitor

### Kafka Broker Metrics
- **MessagesInPerSec**: Rate of incoming messages
- **BytesInPerSec**: Incoming data rate
- **BytesOutPerSec**: Outgoing data rate
- **RequestsPerSec**: Request rate
- **UnderReplicatedPartitions**: Should be 0

### Producer Metrics (per service)
- **record-send-rate**: Messages sent per second
- **record-error-rate**: Failed sends per second
- **request-latency-avg**: Average request latency

### Consumer Metrics (recommendation-service)
- **records-consumed-rate**: Messages consumed per second
- **fetch-latency-avg**: Average fetch latency
- **records-lag-max**: Maximum lag across all partitions

## Integration with Existing Monitoring

The Kafka monitoring setup integrates with:
- Spring Boot Actuator endpoints (already configured)
- Docker health checks (already configured)
- Service logging (already configured)

## Future Enhancements

Consider adding:
1. **Prometheus + Grafana**: For comprehensive metrics dashboards
2. **Alert Manager**: For automated alerting on Kafka issues
3. **Kafka Manager/CMAK**: Alternative Kafka management tool
4. **Distributed Tracing**: Using tools like Jaeger or Zipkin
5. **Log Aggregation**: Using ELK stack or Loki

## References

- [Kafka UI Documentation](https://docs.kafka-ui.provectus.io/)
- [Spring Kafka Documentation](https://spring.io/projects/spring-kafka)
- [Apache Kafka Monitoring](https://kafka.apache.org/documentation/#monitoring)
