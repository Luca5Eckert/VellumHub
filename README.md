# 📖 VellumHub

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.2-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![pgvector](https://img.shields.io/badge/pgvector-enabled-blue)](https://github.com/pgvector/pgvector)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

**A microservices-based book recommendation platform powered by vector similarity search**

VellumHub combines the social aspects of book tracking with AI-powered recommendations. Built with Spring Boot microservices, Apache Kafka event streaming, and PostgreSQL with pgvector extension for intelligent book suggestions.

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Quick Start](#quick-start)
- [API Endpoints](#api-endpoints)
- [Development Status](#development-status)
- [Project Structure](#project-structure)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

**VellumHub** (*Vellum* - ancient parchment for manuscripts + *Hub* - central gathering point) is a distributed book recommendation platform designed around community-driven book discovery. Users can submit books for approval, rate them, track reading progress, and receive personalized recommendations.

### What Makes VellumHub Different

- **Community-Curated Catalog**: Book submissions require admin approval, ensuring quality
- **AI-Powered Recommendations**: Vector similarity search using pgvector for intelligent suggestions
- **Event-Driven Architecture**: Real-time updates via Apache Kafka message streaming
- **Microservices Design**: Independent, scalable services with database-per-service pattern

---

## Key Features

### Current Implementation

| Feature | Status | Description |
|---------|--------|-------------|
| **📚 Book Catalog Management** | ✅ Implemented | CRUD operations for books with metadata (author, ISBN, page count, genres) |
| **✋ Book Submission & Approval** | ✅ Implemented | Users submit books → Admins approve/reject → Catalog updated |
| **⭐ Rating System** | ✅ Implemented | Star ratings that influence personalized recommendations |
| **📖 Reading Progress Tracking** | ✅ Implemented | Track reading status (TO_READ, READING, COMPLETED) with page progress |
| **🎯 Personalized Recommendations** | ✅ Implemented | Vector similarity-based book suggestions using pgvector |
| **🔒 JWT Authentication** | ✅ Implemented | Secure authentication with role-based access (USER/ADMIN) |
| **📊 Event-Driven Updates** | ✅ Implemented | Kafka events for real-time profile and catalog updates |
| **🖥️ Monitoring Dashboard** | ✅ Implemented | Kafka UI for monitoring topics, consumers, and message flow |

### Planned Features

| Feature | Target | Description |
|---------|--------|-------------|
| **💬 Reading Groups** | Future | Collaborative book discussions and group reading lists |
| **🔍 ISBN Lookup** | Future | Automated book metadata fetching via ISBN APIs |
| **👥 Social Features** | Future | Follow users, share reading lists, book reviews |

---

## Architecture

VellumHub follows a **microservices architecture** with **event-driven communication** using Apache Kafka. Each service owns its database following the **Database per Service** pattern.

### System Architecture

```mermaid
graph TB
    subgraph "Client Layer"
        CLIENT[Client Applications]
    end
    
    subgraph "Microservices"
        US[User Service<br/>:8084<br/>Authentication & Users]
        CS[Catalog Service<br/>:8081<br/>Books & Requests]
        RS[Rating Service<br/>:8083<br/>Ratings & Progress]
        RCS[Recommendation Service<br/>:8085<br/>AI Suggestions]
    end
    
    subgraph "Event Streaming"
        KAFKA[Apache Kafka<br/>:9092<br/>Event Backbone]
        KUI[Kafka UI<br/>:8090<br/>Monitoring]
    end
    
    subgraph "Data Layer"
        DB1[(user_db<br/>PostgreSQL)]
        DB2[(catalog_db<br/>PostgreSQL)]
        DB3[(engagement_db<br/>PostgreSQL)]
        DB4[(recommendation_db<br/>PostgreSQL + pgvector)]
    end
    
    CLIENT --> US
    CLIENT --> CS
    CLIENT --> RS
    CLIENT --> RCS
    
    US --> DB1
    CS --> DB2
    RS --> DB3
    RCS --> DB4
    
    CS -->|Book Events| KAFKA
    RS -->|Rating Events| KAFKA
    KAFKA -->|Consume Events| RCS
    
    RCS -->|Bulk Fetch Books| CS
    
    KUI -.Monitor.-> KAFKA
    
    style KAFKA fill:#231F20,stroke:#fff,stroke-width:2px,color:#fff
    style US fill:#6DB33F,stroke:#fff,stroke-width:2px,color:#fff
    style CS fill:#6DB33F,stroke:#fff,stroke-width:2px,color:#fff
    style RS fill:#6DB33F,stroke:#fff,stroke-width:2px,color:#fff
    style RCS fill:#FF6B35,stroke:#fff,stroke-width:2px,color:#fff
    style DB4 fill:#336791,stroke:#fff,stroke-width:2px,color:#fff
```

### Data Flow: Book Recommendation

```mermaid
sequenceDiagram
    participant User
    participant RS as Rating Service
    participant Kafka
    participant RCS as Recommendation Service
    participant DB as recommendation_db<br/>(pgvector)
    participant CS as Catalog Service
    
    User->>RS: Rate book (4.5 stars)
    RS->>Kafka: Publish rating event
    Kafka->>RCS: Consume rating event
    RCS->>DB: Update user profile vector
    
    User->>RCS: GET /recommendations?userId=X
    RCS->>DB: Query similar books<br/>(pgvector cosine similarity)
    DB-->>RCS: Return book IDs + scores
    RCS->>CS: POST /books/bulk<br/>(fetch metadata)
    CS-->>RCS: Return book details
    RCS-->>User: Enriched recommendations
```

### Service Responsibilities

| Service | Port | Database | Responsibilities |
|---------|------|----------|------------------|
| **User Service** | 8084 | `user_db` | User registration, authentication (JWT), user management |
| **Catalog Service** | 8081 | `catalog_db` | Book CRUD, book request submissions, admin approval workflow |
| **Rating Service** | 8083 | `engagement_db` | Star ratings, reading status, page progress tracking |
| **Recommendation Service** | 8085 | `recommendation_db` | Vector similarity search, profile updates, recommendation aggregation |

---

## Technology Stack

### Core Technologies

| Category | Technology | Version | Purpose |
|----------|------------|---------|---------|
| **Language** | Java | 21 LTS | Backend runtime |
| **Framework** | Spring Boot | 3.4.2 | Microservices framework |
| **Database** | PostgreSQL | 15-alpine | Relational data storage |
| **Vector Search** | pgvector | latest | AI-powered similarity search |
| **Message Broker** | Apache Kafka | 7.5.0 | Event streaming |
| **Coordination** | Apache Zookeeper | 7.5.0 | Kafka cluster management |

### Infrastructure

| Technology | Purpose |
|------------|---------|
| **Docker** | Service containerization |
| **Docker Compose** | Multi-container orchestration |
| **Maven** | Build automation |
| **Spring Cloud OpenFeign** | Declarative REST client |

### Security & Data

| Technology | Purpose |
|------------|---------|
| **Spring Security + JWT** | Authentication & authorization |
| **Spring Data JPA** | ORM and data access |
| **Hibernate Vector Type** | Native pgvector integration |

---

## Quick Start

### Prerequisites

- **Docker** 20.10+
- **Docker Compose** 2.0+

Verify installations:

```bash
docker --version
docker-compose --version
```

### Installation

1. **Clone the repository**

   ```bash
   git clone https://github.com/Luca5Eckert/VellumHub.git
   cd VellumHub
   ```

2. **Configure environment variables**

   Create a `.env` file:

   ```env
   # Database Credentials
   POSTGRES_USER=admin
   POSTGRES_PASSWORD=your-secure-password
   
   # JWT Configuration
   JWT_KEY=your-256-bit-secret-key-here
   JWT_EXPIRATION=86400000
   ```

   > ⚠️ **Security**: Use strong, randomly generated values in production

3. **Start all services**

   ```bash
   docker-compose up -d
   ```

   This will start:
   - 4 PostgreSQL databases
   - 4 Spring Boot microservices
   - Apache Kafka + Zookeeper
   - Kafka UI dashboard

4. **Verify deployment**

   ```bash
   docker-compose ps
   ```

   All services should show `Up` or `healthy` status.

5. **Access the services**

   | Service | URL | Purpose |
   |---------|-----|---------|
   | User Service | http://localhost:8084 | Registration & authentication |
   | Catalog Service | http://localhost:8081 | Book management |
   | Rating Service | http://localhost:8083 | Ratings & progress |
   | Recommendation Service | http://localhost:8085 | Get recommendations |
   | Kafka UI | http://localhost:8090 | Monitor Kafka topics |

### Quick Test

Register a user and verify the system:

```bash
# Register a new user
curl -X POST http://localhost:8084/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test User",
    "email": "test@example.com",
    "password": "SecurePass123!"
  }'

# Login to get JWT token
curl -X POST http://localhost:8084/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "SecurePass123!"
  }'
```

---

## API Endpoints

### User Service (Port 8084)

#### Authentication

```http
POST /auth/register
POST /auth/login
```

#### User Management

```http
GET    /users          # List users (paginated, ADMIN only)
GET    /users/{id}     # Get user by ID
PUT    /users/{id}     # Update user
DELETE /users/{id}     # Delete user (ADMIN only)
```

### Catalog Service (Port 8081)

#### Books

```http
GET    /books              # List all approved books (paginated)
GET    /books/{id}         # Get book details
POST   /books              # Create book (ADMIN only - for now)
PUT    /books/{id}         # Update book (ADMIN only)
DELETE /books/{id}         # Delete book (ADMIN only)
POST   /books/bulk         # Get multiple books by IDs
```

#### Book Requests

```http
POST   /book-requests                  # Submit book for approval
POST   /book-requests/{id}/approve     # Approve book (ADMIN only)
```

### Rating Service (Port 8083)

```http
POST   /rating             # Submit a rating
GET    /rating/user/{id}   # Get user's ratings
```

### Recommendation Service (Port 8085)

```http
GET    /api/recommendations?userId={uuid}&limit={n}
```

Returns personalized book recommendations based on:
- User's rating history
- Reading patterns
- Vector similarity with preferred genres/books

---

## Development Status

### Current State

VellumHub is in **active development** with core features implemented and functional:

✅ **Infrastructure**: All 4 microservices deployed with Docker  
✅ **Database**: PostgreSQL with pgvector extension operational  
✅ **Event Streaming**: Kafka topics configured and working  
✅ **Core Features**: Book catalog, ratings, and recommendations working  
✅ **Monitoring**: Kafka UI integrated for observability  

### What's Working

- User registration and JWT authentication
- Book CRUD operations
- Book submission and admin approval workflow
- Star rating system
- Reading progress tracking (status + page count)
- Vector-based book recommendations
- Real-time profile updates via Kafka events

### Known Limitations

- No API Gateway (services exposed directly)
- Limited error handling in some endpoints
- No comprehensive test coverage yet
- Recommendation algorithm is basic (genre-based vectors)
- No frontend application
- No CI/CD pipeline

### Roadmap

#### Short Term
- [ ] Implement comprehensive API Gateway
- [ ] Add unit and integration tests
- [ ] Enhance recommendation algorithm
- [ ] Add OpenAPI/Swagger documentation
- [ ] Improve error handling and validation

#### Long Term
- [ ] Build React/Next.js frontend
- [ ] Add social features (followers, reviews)
- [ ] Implement reading groups
- [ ] ISBN API integration for metadata
- [ ] Deploy to cloud infrastructure (AWS/GCP)
- [ ] Add search and filtering capabilities

---

## Project Structure

```
VellumHub/
│
├── docker-compose.yml              # Multi-service orchestration
├── .env                            # Environment variables (not in git)
│
├── scripts/
│   ├── create-admin-user.sql      # Admin user initialization
│   ├── create-vector-in-recommendation-db.sql  # pgvector setup
│   ├── kafka-health-check.sh      # Kafka monitoring script
│   └── test-service-communication.sh           # Communication tests
│
├── catalog-service/               # Book Catalog Microservice
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/mrs/catalog_service/
│       └── module/
│           ├── book/              # Book management
│           ├── book_request/      # Approval workflow
│           └── book_progress/     # Reading status
│
├── user-service/                  # User Management Microservice
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/mrs/user_service/
│       └── module/
│           ├── auth/              # Authentication & JWT
│           └── user/              # User management
│
├── engagement-service/            # Rating Microservice
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/mrs/engagement_service/
│       └── module/
│           └── rating/            # Star ratings
│
└── recommendation-service/        # Recommendation Engine
    ├── Dockerfile
    ├── pom.xml
    └── src/main/java/com/mrs/recommendation_service/
        └── module/
            ├── book_feature/      # Book vector embeddings
            ├── user_profile/      # User preference vectors
            └── recommendation/    # Recommendation logic
```

---

## Monitoring

### Kafka UI Dashboard

Access the Kafka UI at **http://localhost:8090** to monitor:

- Topic message flow
- Consumer group lag
- Broker health
- Message contents

### Service Health Checks

All services expose Spring Boot Actuator endpoints:

```bash
curl http://localhost:8081/actuator/health  # Catalog Service
curl http://localhost:8083/actuator/health  # Rating Service
curl http://localhost:8084/actuator/health  # User Service
curl http://localhost:8085/actuator/health  # Recommendation Service
```

### Service Logs

```bash
# View logs for specific service
docker logs -f catalog-service

# View Kafka broker logs
docker logs -f kafka
```

---

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to your branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines

- Follow existing code structure and patterns
- Write meaningful commit messages
- Update documentation as needed
- Ensure Docker services build successfully

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Contact

**Project Link**: https://github.com/Luca5Eckert/VellumHub

For questions or issues, please open an issue on GitHub.

---

**VellumHub** - *Where every book finds its reader* 📚
