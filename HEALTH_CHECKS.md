# Health Checks Documentation

This document describes the health checks implemented for all services in the Media Recommendation System.

## Overview

All services now expose health check endpoints that validate their dependencies and operational status. These health checks are used by:
- Docker Compose for container health monitoring
- Kubernetes for liveness and readiness probes
- Monitoring tools for service health tracking

## Endpoints

### Spring Boot Services

All Spring Boot services (catalog-service, user-service, engagement-service, recommendation-service) expose:

**Endpoint:** `/actuator/health`

**Configuration:**
```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always
management.endpoint.health.probes.enabled=true
management.health.livenessState.enabled=true
management.health.readinessState.enabled=true
```

**Response Example:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "kafka": {
      "status": "UP"
    },
    "livenessState": {
      "status": "UP"
    },
    "readinessState": {
      "status": "UP"
    }
  }
}
```

### ML Service

**Endpoint:** `/health`

**Response Example (Healthy):**
```json
{
  "status": "healthy",
  "service": "ml-service",
  "version": "2.0.0",
  "checks": {
    "database": {
      "status": "UP",
      "details": "Connected to recommendation_db"
    }
  }
}
```

**Response Example (Unhealthy):**
```json
{
  "status": "unhealthy",
  "service": "ml-service",
  "version": "2.0.0",
  "checks": {
    "database": {
      "status": "DOWN",
      "error": "connection refused"
    }
  }
}
```

**HTTP Status Codes:**
- `200 OK` - Service is healthy
- `503 Service Unavailable` - Service is unhealthy

## Testing Health Checks

### Using Docker Compose

Start all services:
```bash
docker-compose up -d
```

Check health status:
```bash
docker-compose ps
```

All services should show status as `healthy` once fully started.

### Manual Testing

**Spring Boot Services:**
```bash
# Catalog Service
curl http://localhost:8081/actuator/health | jq

# User Service
curl http://localhost:8084/actuator/health | jq

# Engagement Service
curl http://localhost:8083/actuator/health | jq

# Recommendation Service
curl http://localhost:8085/actuator/health | jq
```

**ML Service:**
```bash
curl http://localhost:5000/health | jq
```

### Kubernetes Probes

**Liveness Probe:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 30
```

**Readiness Probe:**
```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
```

## Health Check Components

### Database (PostgreSQL)

**What it checks:**
- Database connection is available
- Can execute queries
- Connection pool is healthy

**Spring Boot:** Auto-configured by Spring Data JPA
**ML Service:** Manual check with `SELECT 1` query

### Kafka

**What it checks:**
- Kafka broker is reachable
- Producer/Consumer connections are healthy

**Spring Boot:** Auto-configured by spring-kafka
**ML Service:** Not applicable

### Application State

**Liveness:**
- Application is running
- No deadlocks or unrecoverable errors

**Readiness:**
- Application is ready to receive traffic
- All dependencies are available
- Initialization is complete

## Docker Health Check Configuration

### Spring Boot Services
```yaml
healthcheck:
  test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s
```

### ML Service
```yaml
healthcheck:
  test: ["CMD-SHELL", "python -c \"import requests; import sys; r = requests.get('http://localhost:5000/health', timeout=5); sys.exit(0 if r.status_code == 200 else 1)\" || exit 1"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s
```

## Troubleshooting

### Service Shows as Unhealthy

1. Check service logs:
   ```bash
   docker logs <service-name>
   ```

2. Check health endpoint directly:
   ```bash
   curl http://localhost:<port>/actuator/health
   ```

3. Common issues:
   - Database not ready: Wait for PostgreSQL to be healthy
   - Kafka not ready: Ensure Kafka and Zookeeper are running
   - Application startup: Check if start_period is sufficient

### Health Check Times Out

- Increase `start_period` if service takes longer to initialize
- Check if service is actually listening on the expected port
- Verify no firewall or network issues

### Database Shows as DOWN

- Check PostgreSQL is running: `docker ps | grep media-db`
- Verify connection string in environment variables
- Check database exists: `docker exec -it media-db psql -U user -l`

## Best Practices

1. **Always check health status** before deploying
2. **Set appropriate start_period** based on service initialization time
3. **Monitor health check logs** for patterns
4. **Use readiness probes** to prevent traffic to unhealthy instances
5. **Use liveness probes** to restart failed containers

## References

- [Spring Boot Actuator Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Docker Health Check Documentation](https://docs.docker.com/engine/reference/builder/#healthcheck)
- [Kubernetes Probes Documentation](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)
