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
  - [System Architecture](#system-architecture)
  - [Architecture Evolution](#architecture-evolution)
  - [Database Architecture](#database-architecture)
  - [Event-Driven Communication](#event-driven-communication)
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
        CS[Catalog Service<br/>:8081<br/>Books, Requests & Progress]
        RS[Engagement Service<br/>:8083<br/>Ratings]
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
    participant RS as Engagement Service
    participant Kafka
    participant RCS as Recommendation Service
    participant DB as recommendation_db<br/>(pgvector)
    participant CS as Catalog Service
    
    User->>RS: Rate book (4.5 stars)
    RS->>Kafka: Publish rating event (created-rating)
    Kafka->>RCS: Consume rating event
    RCS->>DB: Update user profile vector
    
    User->>RCS: GET /api/recommendations
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
| **Catalog Service** | 8081 | `catalog_db` | Book CRUD, book request submissions, admin approval workflow, reading progress & status tracking |
| **Engagement Service** | 8083 | `engagement_db` | Star ratings (create, update, query with filters) |
| **Recommendation Service** | 8085 | `recommendation_db` | Vector similarity search, profile updates, recommendation aggregation |

---

## Architecture Evolution

VellumHub has evolved from a tightly-coupled system with performance bottlenecks to a modern, scalable microservices platform. This transformation demonstrates our commitment to engineering excellence and continuous improvement.

### From ML Service to pgvector

```mermaid
graph LR
    V1[v1.0<br/>With ML Service<br/>Performance Issues]
    V2[v2.0<br/>Current Architecture<br/>pgvector + Books]
    
    V1 -->|Eliminated ML Service<br/>60-75% Latency Reduction<br/>Domain Focus| V2
    
    style V1 fill:#ff6b6b,stroke:#c92a2a,stroke-width:2px,color:#fff
    style V2 fill:#51cf66,stroke:#2f9e44,stroke-width:2px,color:#fff
```

### Version 1.0: Architecture with ML Service

The original architecture relied on an external Python Flask ML Service for recommendations, which created critical performance and operational challenges.

**Architecture:**
```
┌──────────┐     REST      ┌─────────────────┐     REST      ┌────────────┐
│  Client  │ ────────────> │ Recommendation  │ ────────────> │ ML Service │
└──────────┘               │    Service      │               │  (Flask)   │
                           └─────────────────┘               └────────────┘
                                    │                               │
                                    └───────── Shared DB ───────────┘
```

**Key Problems:**

| Problem | Impact |
|---------|--------|
| **Excessive Latency** | 3+ network hops per request (~300-500ms) |
| **Tight Coupling** | ML Service failure brought down recommendations |
| **Redundant Computation** | Full recalculation on every request |
| **Poor API Design** | Client had to orchestrate multiple service calls |

### Version 2.0: Current Architecture (pgvector + Book Platform)

The current architecture eliminates the ML Service entirely, integrating vector similarity search directly into PostgreSQL with pgvector while focusing on book recommendations.

**Architecture:**
```
┌──────────┐                 ┌─────────────────┐
│  Client  │ ──────────────> │   Book Catalog  │ ─┐
└──────────┘                 │     Service     │  │
      │                      └─────────────────┘  │
      │                                            │ Kafka
      │                      ┌─────────────────┐  │ Events
      ├────────────────────> │ Engagement      │ ─┤
      │                      │    Service      │  │
      │                      └─────────────────┘  │
      │                               │            │
      │                               ↓            ↓
      │                      ┌──────────────────────┐
      └────────────────────> │  Recommendation      │
                             │  Service (pgvector)  │
                             └──────────────────────┘
                                       │
                           ┌───────────┴───────────┐
                           │   recommendation_db   │
                           │   (pgvector enabled)  │
                           └───────────────────────┘
```

**Key Improvements:**

| Aspect | v1.0 (With ML Service) | v2.0 (Current) |
|--------|------------------------|----------------|
| **Architecture** | 5 services (incl. Flask ML) | 4 services (pure Java) |
| **Domain** | General media | Books only |
| **Latency** | ~300-500ms | ~80-120ms |
| **Recommendation Engine** | External ML Service (REST) | Native pgvector (SQL) |
| **Profile Updates** | On-demand recalculation | Real-time via Kafka events |
| **API Design** | Client orchestration | Server-side aggregation |
| **Interactions** | LIKE, DISLIKE, WATCH | ⭐ Star Ratings (0–5)<br/>📖 Reading Status |
| **Catalog** | Open creation | 🛡️ Admin approval required |

**Technical Achievements:**
- ✅ **60-75% latency reduction** (300-500ms → 80-120ms)
- ✅ **Pure Java stack** - eliminated Python dependency
- ✅ **Event-driven architecture** - Kafka for real-time profile updates
- ✅ **Vector similarity search** - PostgreSQL pgvector with HNSW indexing
- ✅ **Domain specialization** - Book-focused features (ISBN, authors, page tracking)
- ✅ **Enhanced user engagement** - Star ratings and reading progress tracking

---

## Database Architecture

VellumHub implements the **Database per Service** pattern, ensuring complete autonomy and independent scalability for each microservice.

```mermaid
graph TB
    subgraph "User Service"
        US_APP[User Service<br/>Application]
        US_DB[(user_db<br/>PostgreSQL 15)]
        US_APP --> US_DB
    end
    
    subgraph "Catalog Service"
        CS_APP[Catalog Service<br/>Application]
        CS_DB[(catalog_db<br/>PostgreSQL 15)]
        CS_APP --> CS_DB
    end
    
    subgraph "Engagement Service"
        RS_APP[Engagement Service<br/>Application]
        RS_DB[(engagement_db<br/>PostgreSQL 15)]
        RS_APP --> RS_DB
    end
    
    subgraph "Recommendation Service"
        REC_APP[Recommendation<br/>Service]
        REC_DB[(recommendation_db<br/>PostgreSQL 15<br/>+ pgvector extension)]
        REC_APP --> REC_DB
    end
    
    subgraph "Event Streaming"
        KAFKA[Apache Kafka<br/>Event Backbone]
    end
    
    CS_APP -->|Book Events| KAFKA
    RS_APP -->|Rating Events| KAFKA
    KAFKA -->|Profile Updates| REC_APP
    
    style US_DB fill:#336791,stroke:#fff,stroke-width:2px,color:#fff
    style CS_DB fill:#336791,stroke:#fff,stroke-width:2px,color:#fff
    style RS_DB fill:#336791,stroke:#fff,stroke-width:2px,color:#fff
    style REC_DB fill:#FF6B35,stroke:#fff,stroke-width:2px,color:#fff
    style KAFKA fill:#231F20,stroke:#fff,stroke-width:2px,color:#fff
```

### Database Schemas

| Database | Service | Key Tables | Purpose |
|----------|---------|------------|---------|
| **user_db** | User Service | `users`, `roles`, `user_preferences` | User accounts, authentication credentials, role-based permissions |
| **catalog_db** | Catalog Service | `books`, `book_requests`, `book_progress` | Book metadata, submission approval workflow, reading status |
| **engagement_db** | Engagement Service | `ratings` | Star ratings linked to users and books |
| **recommendation_db** | Recommendation Service | `book_features`, `user_profiles`, `recommendations` | Vector embeddings (book features + user preference vectors) |

### pgvector Implementation

The Recommendation Service database leverages **pgvector** extension for high-performance similarity search:

**Vector Storage:**
```sql
-- Book feature vectors (genre-based embeddings)
-- 15 dimensions: one per genre (one-hot encoding for 15 book genres)
CREATE TABLE book_features (
    book_id UUID PRIMARY KEY,
    embedding vector(15),  -- Genre-based embedding
    popularity_score DOUBLE PRECISION,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL
);

-- User profile vectors (preference embeddings)
-- 15 dimensions: matches book vectors for direct cosine similarity computation
CREATE TABLE user_profiles (
    user_id UUID PRIMARY KEY,
    profile_vector vector(15),  -- User genre preferences
    total_engagement_score DOUBLE PRECISION,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL
);
```

**Vector Similarity Query:**
```sql
-- Find similar books using cosine distance
SELECT book_id, (embedding <=> $1::vector) as similarity
FROM book_features
ORDER BY embedding <=> $1::vector
LIMIT 10;
```

**How It Works:**
- **Genre Encoding**: Each of the 15 dimensions represents one book genre (Fantasy, Sci-Fi, Horror, Romance, etc.)
- **Book Vectors**: One-hot encoding where dimension = 1.0 if book belongs to that genre, 0.0 otherwise
- **User Vectors**: Weighted preferences updated based on ratings (higher weights for genres user enjoys)
- **Similarity Search**: PostgreSQL pgvector computes cosine distance between user preferences and book genres

**Performance Optimization:**
- **HNSW Index** (Hierarchical Navigable Small World) for approximate nearest neighbor search
- **Fast lookups** on datasets with 100K+ book vectors (typically under 100ms)
- **Real-time updates** via Kafka event consumers

---

## Event-Driven Communication

VellumHub uses **Apache Kafka** as the central nervous system for asynchronous communication between services.

### Kafka Topics & Data Flow

```mermaid
graph LR
    subgraph "Producers"
        CS[Catalog Service]
        ES[Engagement Service]
    end
    
    subgraph "Apache Kafka"
        T1[created-book]
        T2[updated-book]
        T3[deleted-book]
        T4[created-rating]
    end
    
    subgraph "Consumers"
        REC[Recommendation Service]
    end
    
    CS -->|New Book| T1
    CS -->|Book Updated| T2
    CS -->|Book Deleted| T3
    ES -->|User Rating| T4
    
    T1 --> REC
    T2 --> REC
    T3 --> REC
    T4 --> REC
    
    REC -->|Creates/Updates<br/>Book Features| DB[(recommendation_db)]
    REC -->|Updates<br/>User Profiles| DB
    
    style T1 fill:#231F20,stroke:#fff,stroke-width:2px,color:#fff
    style T2 fill:#231F20,stroke:#fff,stroke-width:2px,color:#fff
    style T3 fill:#231F20,stroke:#fff,stroke-width:2px,color:#fff
    style T4 fill:#231F20,stroke:#fff,stroke-width:2px,color:#fff
```

### Event Types

| Event | Producer | Consumer | Trigger | Action |
|-------|----------|----------|---------|--------|
| **created-book** | Catalog Service | Recommendation Service | Book approved by admin | Create `BookFeature` with genre-based vector embedding |
| **updated-book** | Catalog Service | Recommendation Service | Book metadata changed | Update `BookFeature` vector |
| **deleted-book** | Catalog Service | Recommendation Service | Book removed from catalog | Delete `BookFeature` |
| **created-rating** | Engagement Service | Recommendation Service | User rates a book | Update `UserProfile` preference vector |

### Benefits of Event-Driven Architecture

✅ **Loose Coupling** - Services communicate without direct dependencies  
✅ **Scalability** - Consumers process events independently at their own pace  
✅ **Resilience** - Kafka retains events; consumers can replay if failures occur  
✅ **Real-Time Updates** - User profiles updated immediately when ratings change  
✅ **Audit Trail** - All state changes captured in event log  

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
   | Catalog Service | http://localhost:8081 | Book management & reading progress |
   | Engagement Service | http://localhost:8083 | Ratings |
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
POST   /users          # Create user (ADMIN only)
GET    /users          # List all users (paginated)
GET    /users/{id}     # Get user by ID
GET    /users/me       # Get authenticated user's own profile
PUT    /users/{id}     # Update user (ADMIN only)
DELETE /users/{id}     # Delete user (ADMIN only)
```

### Catalog Service (Port 8081)

#### Books

```http
GET    /books              # List all approved books (paginated)
GET    /books/{id}         # Get book details
POST   /books              # Create book (ADMIN only)
PUT    /books/{id}         # Update book (ADMIN only)
DELETE /books/{id}         # Delete book (ADMIN only)
POST   /books/bulk         # Get multiple books by IDs (internal use)
```

#### Book Requests

```http
POST   /book-requests                    # Submit book for approval
POST   /book-requests/{id}/approve       # Approve book request (ADMIN only)
```

#### Book Progress

```http
POST   /book-progress/{bookId}/status    # Set reading status (TO_READ, READING, COMPLETED)
PUT    /book-progress/{bookId}/progress  # Update current page number
DELETE /book-progress/{bookId}           # Remove book from reading list
GET    /book-progress/reading-list       # Get authenticated user's reading list
```

### Engagement Service (Port 8083)

```http
POST   /rating              # Submit a rating (0–5 stars)
GET    /rating/{userId}     # Get ratings by user ID (filters: minStars, maxStars, from, to; paginated)
GET    /rating/me           # Get authenticated user's own ratings (same filters)
PUT    /rating/{ratingId}   # Update an existing rating
```

### Recommendation Service (Port 8085)

```http
GET    /api/recommendations?limit={n}&offset={n}
```

> Authentication is required (JWT). The user ID is extracted from the token — no `userId` query parameter is needed.

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
- Unit tests exist across all services; integration and end-to-end tests are still pending
- Recommendation algorithm is basic (genre-based vectors)
- Frontend application is under active development — see [VellumHubFront](https://github.com/Luca5Eckert/VellumHubFront)
- No CI/CD pipeline

### Roadmap

#### Short Term
- [ ] Implement comprehensive API Gateway
- [ ] Add integration and end-to-end tests
- [ ] Enhance recommendation algorithm
- [ ] Improve error handling and validation

#### Long Term
- [ ] Complete React/Next.js frontend (in progress at [VellumHubFront](https://github.com/Luca5Eckert/VellumHubFront))
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
curl http://localhost:8083/actuator/health  # Engagement Service
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
