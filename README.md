# 🎬 Media Recommendation System

> A next-generation, event-driven media recommendation platform inspired by Netflix and Spotify, built with microservices architecture.

[![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-231F20?style=for-the-badge&logo=apache-kafka&logoColor=white)](https://kafka.apache.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)

## 🚀 Overview

The **Media Recommendation System** is a scalable, distributed application designed to deliver personalized content recommendations to users in real-time. By leveraging an **event-driven architecture**, the system ensures seamless communication between microservices through **Apache Kafka**, enabling asynchronous data processing and real-time analytics.

### 🏗️ Architecture

The system follows a **microservices architecture** with event-driven communication. Each microservice follows the **Database per Service** pattern, ensuring complete isolation and independent scalability.

```mermaid
graph TB
    subgraph "Microservices Layer"
        US[User Service]
        CS[Catalog Service]
        ES[Engagement Service]
        RS[Recommendation Service]
    end
    
    subgraph "Event Streaming"
        KAFKA[Apache Kafka<br/>Event Backbone]
    end
    
    subgraph "Persistence Layer"
        USER_DB[(user_db)]
        CATALOG_DB[(catalog_db)]
        ENGAGEMENT_DB[(engagement_db)]
        RECOMMENDATION_DB[(recommendation_db)]
    end
    
    US --> USER_DB
    CS --> CATALOG_DB
    ES --> ENGAGEMENT_DB
    ES -->|Publishes Events| KAFKA
    KAFKA -->|Consumes Events| RS
    RS --> RECOMMENDATION_DB
    
    style KAFKA fill:#231F20,stroke:#fff,stroke-width:2px,color:#fff
    style US fill:#6DB33F,stroke:#fff,stroke-width:2px,color:#fff
    style CS fill:#6DB33F,stroke:#fff,stroke-width:2px,color:#fff
    style ES fill:#6DB33F,stroke:#fff,stroke-width:2px,color:#fff
    style RS fill:#6DB33F,stroke:#fff,stroke-width:2px,color:#fff
```

**Key Components:**
- 🔐 **User Service** → `user_db`: Manages user authentication, profiles, and preferences
- 📚 **Catalog Service** → `catalog_db`: Maintains the catalog of movies, series, and metadata
- 💡 **Engagement Service** → `engagement_db`: Tracks user interactions (views, likes, clicks, ratings) and publishes events to Kafka
- 🎯 **Recommendation Service** ← Kafka → `recommendation_db`: Consumes engagement events and generates personalized recommendations
- 📨 **Apache Kafka**: Acts as the central event bus for asynchronous communication between services

> **📝 Local Development Note**: For simplified local development with Docker Compose, all four databases (`user_db`, `catalog_db`, `engagement_db`, `recommendation_db`) are hosted within a single PostgreSQL 15 container. This approach maintains logical database separation while reducing infrastructure complexity in the development environment. In production, each database would be deployed as an independent instance to ensure complete service isolation.

## 📦 Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| **Containerization** | Docker & Docker Compose | Latest |
| **Database** | PostgreSQL | 15 |
| **Message Broker** | Apache Kafka | 7.3.0 (Confluent) |
| **Coordination** | Apache Zookeeper | 7.3.0 (Confluent) |
| **Backend Services** | Spring Boot (Java) | 4.0.0 |
| **JDK** | Java | 21 |

## 🗄️ Database Architecture

The system follows the **Database per Service** pattern, a core principle of microservices architecture that ensures:
- ✅ **Service Isolation**: Each microservice owns its data and schema
- ✅ **Independent Scalability**: Databases can be scaled independently based on service needs
- ✅ **Technology Flexibility**: Each service can choose the optimal database technology
- ✅ **Fault Isolation**: Database issues in one service don't cascade to others

| Database | Owner Service | Purpose |
|----------|---------------|---------|
| `user_db` | User Service | User management and authentication |
| `catalog_db` | Catalog Service | Media catalog and metadata |
| `engagement_db` | Engagement Service | User interaction tracking (views, likes, clicks) |
| `recommendation_db` | Recommendation Service | Recommendation results and cache |

> 💡 **Auto-Initialization**: All databases are automatically created during the first startup via the `./scripts/create-databases.sql` initialization script.

> 🏗️ **Local Development Setup**: For the local Docker Compose environment, all four databases run within a single PostgreSQL 15 container instance. This simplified approach maintains logical separation while reducing resource overhead for development. In production deployments, each database would be provisioned as a separate instance to achieve full physical isolation.

## ⚙️ Prerequisites

Before running the project, ensure you have the following installed:

- 🐳 **Docker**: [Install Docker](https://docs.docker.com/get-docker/)
- 🐙 **Docker Compose**: [Install Docker Compose](https://docs.docker.com/compose/install/)

Verify your installations:
```bash
docker --version
docker-compose --version
```

## 🚀 Getting Started

### Quick Start

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Luca5Eckert/media-recommendation-system.git
   cd media-recommendation-system
   ```

2. **Start the infrastructure**:
   ```bash
   docker-compose up -d
   ```

3. **Verify the services are running**:
   ```bash
   docker-compose ps
   ```

That's it! 🎉 The infrastructure is now running with:
- ✅ PostgreSQL with 4 databases automatically initialized
- ✅ Apache Kafka ready for event streaming
- ✅ Zookeeper managing Kafka coordination

### 📂 Auto-Initialization

The `./scripts/create-databases.sql` file contains SQL commands to create all required databases. This script is automatically executed when PostgreSQL starts for the first time, thanks to Docker's `docker-entrypoint-initdb.d` mechanism.

## 🔧 Useful Commands

### Docker Management

**View all running containers:**
```bash
docker-compose ps
```

**Stop all services:**
```bash
docker-compose down
```

**Restart services:**
```bash
docker-compose restart
```

**Remove volumes (⚠️ deletes all data):**
```bash
docker-compose down -v
```

### Database Access

**View PostgreSQL logs:**
```bash
docker logs media-db
```

**Access PostgreSQL CLI:**
```bash
docker exec -it media-db psql -U user -d user_db
```

**List all databases:**
```bash
docker exec -it media-db psql -U user -d user_db -c "\l"
```

**Connect to a specific database:**
```bash
docker exec -it media-db psql -U user -d catalog_db
```

### Kafka Management

**View Kafka logs:**
```bash
docker logs kafka
```

**List Kafka topics:**
```bash
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092
```

**Create a new topic:**
```bash
docker exec -it kafka kafka-topics --create --topic engagement-events --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

## 🌐 Service Ports

| Service | Port | Access |
|---------|------|--------|
| PostgreSQL | `5432` | `localhost:5432` |
| Apache Kafka | `9092` | `localhost:9092` |
| Zookeeper | `2181` | `localhost:2181` |

### Database Connection Details

```
Host: localhost
Port: 5432
User: user
Password: password
Databases: user_db, catalog_db, engagement_db, recommendation_db
```

## 📈 Project Status

🚧 **This project is currently in MVP/Infrastructure Setup phase**

### ✅ Completed
- [x] Docker Compose configuration
- [x] PostgreSQL setup with multi-database architecture
- [x] Apache Kafka and Zookeeper integration
- [x] Database auto-initialization scripts
- [x] Microservices project structure (catalog-service, engagement-service)

### 🛣️ Roadmap / Next Steps

1. **Microservices Implementation**
   - [ ] Complete User Service (Authentication & Authorization)
   - [ ] Complete Catalog Service (Media CRUD operations)
   - [ ] Complete Engagement Service (Event publishing to Kafka)
   - [ ] Implement Recommendation Engine (Kafka consumer + ML algorithms)

2. **API Layer**
   - [ ] RESTful APIs for each service
   - [ ] API Gateway for unified access
   - [ ] OpenAPI/Swagger documentation

3. **Event Streaming**
   - [ ] Define Kafka topic schemas (Avro/JSON)
   - [ ] Implement producers and consumers
   - [ ] Event sourcing patterns

4. **Machine Learning**
   - [ ] Collaborative filtering algorithms
   - [ ] Content-based filtering
   - [ ] Hybrid recommendation strategies
   - [ ] Model training pipeline

5. **Frontend**
   - [ ] Web application (React/Vue/Angular)
   - [ ] Mobile applications (React Native/Flutter)

6. **DevOps & Production**
   - [ ] CI/CD pipelines
   - [ ] Kubernetes orchestration
   - [ ] Monitoring and observability (Prometheus, Grafana)
   - [ ] Distributed tracing (Jaeger/Zipkin)

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is licensed under the MIT License.

## 📧 Contact

For questions or suggestions, please open an issue in the repository.

---

⭐ **If you find this project useful, please consider giving it a star!** ⭐
