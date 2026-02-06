# 📊 Project Analysis — BookEra

> **Complete Analysis of Current State, New Identity, and Execution Plan**  
> **Date:** February 2026

---

## 📋 Executive Summary

**BookEra** — *Book* + *Era* (a new era of reading) — is a book-focused recommendation platform inspired by Letterboxd, but designed specifically for books. The project has undergone a **strategic pivot** — shifting from a general media platform to a specialized book community where users can catalog, rate, track reading progress, and receive personalized book recommendations.

### Identity Evolution

| Aspect | Previous Identity | New Identity |
|--------|------------------|--------------|
| **Concept** | General media recommendation platform (Netflix/Spotify-like) | Book-focused platform (Letterboxd for books) |
| **Content** | Movies, TV shows, music, and other media | Books exclusively |
| **Core Interactions** | Like, Dislike, Watch | Star ratings (0–5), Reading status, Reviews |
| **Target Users** | General media consumers | Book readers and literary communities |

---

## 🏗️ Current State of the Project

### Architecture Overview (v2)

The system consists of **4 Spring Boot microservices** with the Recommendation Service acting as an **Aggregator**. Recommendations are computed via native SQL cosine distance queries against pgvector embeddings, and results are enriched through a bulk fetch call to the Catalog Service.

```
Client → Recommendation Service (pgvector query) → recommendation_db
                ↓
         Bulk fetch enrichment → Catalog Service /books/bulk
                ↓
         Returns fully enriched recommendations in a single response
```

### ✅ Implemented Components

#### 1. **Infrastructure (100% Complete)**
- ✅ Docker Compose configured for all services
- ✅ PostgreSQL 15 with 4 isolated containers
- ✅ pgvector extension enabled with HNSW indexing
- ✅ Apache Kafka + Zookeeper for asynchronous communication
- ✅ Optimized multi-stage Dockerfiles for all services
- ✅ SQL init script for vector extension and index creation

#### 2. **Catalog Service → Book Catalog Service (needs refactoring)**
- ✅ Entity with fields: id, title, description, releaseYear, mediaType, coverUrl, genres
- ✅ Full CRUD: Create, Read (by ID), Read All (paginated), Delete
- ✅ Bulk fetch endpoint for batch retrieval by IDs
- ✅ Kafka integration: publishes events on create, update, delete topics
- ✅ JWT security with `@PreAuthorize` for admin operations
- ⚠️ **Needs refactoring:** Rename `Media` → `Book`, add book-specific fields (author, ISBN, pageCount), add admin approval workflow

#### 3. **User Service (90% Complete)**
- ✅ `UserEntity` with fields: id, name, email, password, role
- ✅ Full authentication: `/auth/register` and `/auth/login`
- ✅ User CRUD with JWT and role-based access control
- ✅ User preferences (genres)
- ⚠️ **Needs:** Reading status tracking, user profile enhancements for book platform

#### 4. **Engagement Service → Reading & Rating Service (needs refactoring)**
- ✅ Interaction entity with event publishing
- ✅ Kafka event publishing
- ⚠️ **Needs refactoring:** Replace LIKE/DISLIKE/WATCH with star ratings (0–5), reading status (TO_READ, READING, COMPLETED), and page tracking

#### 5. **Recommendation Service (85% Complete)**
- ✅ pgvector-powered vector similarity search
- ✅ Hybrid scoring algorithm (70% vector + 30% popularity)
- ✅ Kafka consumers for media and interaction events
- ✅ Catalog Service integration via OpenFeign
- ⚠️ **Needs:** Adapt recommendations to leverage star ratings, reading status, and book-specific data

---

### 📊 Completion Metrics by Service

| Service | Backend | API | Tests | Documentation | Overall |
|---------|:-------:|:---:|:-----:|:-------------:|:-------:|
| **Book Catalog Service** | ✅ 70% | ⚠️ 60% | ⚠️ 10% | ⚠️ 30% | **43%** |
| **User Service** | ✅ 85% | ✅ 80% | ⚠️ 10% | ⚠️ 30% | **51%** |
| **Reading & Rating Service** | ⚠️ 50% | ⚠️ 40% | ⚠️ 10% | ⚠️ 20% | **30%** |
| **Recommendation Service** | ✅ 75% | ⚠️ 65% | ⚠️ 10% | ⚠️ 30% | **45%** |
| **Infrastructure** | ✅ 100% | N/A | N/A | ✅ 85% | **93%** |

---

## 🎯 MVP (1.0) — Feature Definition

The MVP transforms the platform into a book-focused experience with the following core features:

### 📚 1. Book Catalog with Admin Approval
- A comprehensive catalog of books with metadata (title, author, ISBN, genre, cover, page count, description)
- Any authenticated user can **submit** a book to the catalog
- Submitted books enter a **pending approval** state
- Only **ADMIN** users can approve or reject book submissions
- Approved books become visible in the public catalog

### ⭐ 2. Star Rating System
- Users can rate books from **0 to 5 stars** (supporting half-star increments)
- Ratings contribute to an aggregate book score displayed in the catalog
- Ratings feed into the recommendation engine for personalization
- Users can update their rating at any time

### 📖 3. Reading Status Tracking
- Users can set a reading status for each book:
  - **TO_READ** — Book is on the user's reading list
  - **READING** — Currently reading, with current page tracking
  - **COMPLETED** — Finished reading the book
- Users can update their current page while in READING status
- Reading progress is used by the recommendation engine

### 🎯 4. Personalized Recommendations
- Recommendations based on:
  - Books the user is **currently reading**
  - Books the user has **interacted with** (rated, added to list)
  - Books the user has **rated highly** (4+ stars)
- Vector similarity search using pgvector for content-based recommendations
- Hybrid scoring: vector similarity + popularity + user rating patterns

---

## 🔄 Architecture Evolution

The system underwent two major evolutions — first to solve critical performance and coupling problems, then to pivot toward a book-focused platform.

### Previous Architecture (v1) — General Media + ML Service

```
Client → Recommendation Service → ML Service (Flask/Python) → recommendation_db
                                        ↓
                                  Computes scores
                                        ↓
         Recommendation Service ← Returns media IDs
                ↓
         Client must call Catalog Service separately for full media data
```

#### Why v1 was replaced

The original architecture had four critical problems that made it unsustainable:

| Problem | Impact | Root Cause |
|---------|--------|------------|
| **Excessive latency** | Every recommendation request required 3+ network hops (Recommendation Service → ML Service → Database → back) | The ML Service was a synchronous REST intermediary between the Java backend and the database |
| **Tight coupling** | A failure in the Python ML Service brought down recommendations entirely | The Java Recommendation Service and the Flask ML Service shared the same database, creating hidden dependencies |
| **Redundant computation** | Recommendations were recalculated from scratch on every single request, wasting CPU and increasing response times | No caching or pre-computation strategy — the ML Service ran the full scoring algorithm per request |
| **Weak API abstraction** | The front-end had to orchestrate multiple service calls manually (get recommendations, then fetch media details separately) | The Recommendation Service returned only media IDs, forcing the client to make additional calls to the Catalog Service |

### Architecture v2 — General Media + pgvector

These problems were solved by eliminating the ML Service entirely and moving vector similarity search into PostgreSQL with pgvector:

```
Client → Recommendation Service (pgvector query) → recommendation_db
                ↓
         Bulk fetch enrichment → Catalog Service /media/bulk
                ↓
         Returns fully enriched recommendations in a single response
```

#### How v2 solved each problem

| Problem | v1 | v2 Solution |
|---------|-----|-------------|
| **Excessive latency** | 3+ network hops per request | Single database query using pgvector's `<=>` cosine distance operator + one bulk fetch |
| **Tight coupling** | Java ↔ Python ↔ shared DB | Pure Java stack — ML Service eliminated, vector queries run natively in PostgreSQL |
| **Redundant computation** | Full recalculation per request | User profiles updated in real-time via Kafka events; recommendations are a simple vector lookup |
| **Weak API abstraction** | Client orchestrates multiple calls | Aggregator pattern — Recommendation Service enriches results server-side via `/media/bulk` before returning |

### Architecture v3 (Current) — Book Platform

```
Client → Recommendation Service (pgvector query) → recommendation_db
                ↓
         Bulk fetch enrichment → Book Catalog Service /books/bulk
                ↓
         Returns enriched book recommendations with ratings & reading status
```

### Evolution Summary

| Aspect | v1 (Original) | v2 (Optimized) | v3 (Book Platform) |
|--------|---------------|----------------|---------------------|
| **Concept** | General media platform | Optimized media platform | Book-focused platform |
| **Content** | Movies, TV, Music | Movies, TV, Music | Books only |
| **Services** | 5 (incl. Flask ML) | 4 (pure Java) | 4 (refactored for books) |
| **Interactions** | Like, Dislike, Watch | Like, Dislike, Watch | Star ratings (0–5), Reading status |
| **Catalog** | Open creation | Open creation | Submission + Admin approval |
| **Recommendation** | ML Service (sync REST) | pgvector (native SQL) | pgvector + reading patterns |
| **Latency** | 3+ network hops | 1 DB query + 1 bulk fetch | 1 DB query + 1 bulk fetch |
| **Profile Updates** | On-demand recalculation | Real-time via Kafka | Real-time via Kafka |
| **Data Enrichment** | Client-side orchestration | Server-side aggregation | Server-side aggregation |

---

## 📅 Sprint Planning

### Sprint 1–2: Previous (Completed) ✅

<details>
<summary><strong>Sprint 1 — Autonomy & Vector Infrastructure ✅</strong></summary>

- [x] pgvector setup: `vector` extension enabled in PostgreSQL
- [x] Local media embeddings: `MediaFeature` entity with `vector` columns
- [x] Kafka sync (vectorized): Consumer converts genres into vectors

</details>

<details>
<summary><strong>Sprint 2 — Geometric Intelligence (ML-Service Removal) ✅</strong></summary>

- [x] User vectorization: `UserProfile` with real-time `profile_vector`
- [x] Vector similarity query: Cosine distance search implementation
- [x] ML-Service shutdown: Flask API removed

</details>

---

### Sprint 3: Book Catalog Foundation 🔄
```
Objective: Transform the catalog service into a book-specific catalog with admin approval workflow
```
- [ ] Rename `Media` entity to `Book` with book-specific fields (author, ISBN, pageCount, publisher)
- [ ] Add `ApprovalStatus` enum (PENDING, APPROVED, REJECTED) to Book entity
- [ ] Create book submission endpoint (any authenticated user)
- [ ] Create admin approval/rejection endpoints
- [ ] Update bulk fetch endpoint for books
- [ ] Update Kafka topics to book events (`create-book`, `update-book`, `delete-book`)
- [ ] Update catalog listing to only show APPROVED books by default

### Sprint 4: Star Ratings & Reading Status 📋
```
Objective: Implement the star rating system and reading status tracking
```
- [ ] Replace interaction types (LIKE/DISLIKE/WATCH) with star rating model (0–5 stars)
- [ ] Create `ReadingStatus` entity (TO_READ, READING, COMPLETED) with page tracking
- [ ] Implement `POST /books/{bookId}/rating` — Submit or update a star rating
- [ ] Implement `GET /books/{bookId}/ratings` — Get aggregate rating for a book
- [ ] Implement `POST /books/{bookId}/status` — Set or update reading status
- [ ] Implement `GET /users/{userId}/reading-list` — Get user's reading list with statuses
- [ ] Implement `PUT /books/{bookId}/progress` — Update current page (for READING status)
- [ ] Publish rating and status events to Kafka for recommendation updates

### Sprint 5: Book-Based Recommendations 📋
```
Objective: Adapt the recommendation engine to leverage book-specific data
```
- [ ] Update `MediaFeature` → `BookFeature` with book-specific embedding vectors
- [ ] Incorporate star ratings into user profile vector updates
- [ ] Incorporate reading status into recommendation signals
- [ ] Weight recommendations: currently reading > high ratings > to-read list
- [ ] Paginated recommendation responses with full book data enrichment
- [ ] Cold-start handling: recommend popular and highly-rated books for new users

### Sprint 6: Integration, Testing & Polish 📋
```
Objective: End-to-end integration, testing, and documentation
```
- [ ] Unit tests for all services (minimum 50% coverage)
- [ ] Integration tests for book submission → approval → catalog flow
- [ ] Integration tests for rating → recommendation pipeline
- [ ] End-to-end test: register → submit book → approve → rate → get recommendations
- [ ] OpenAPI/Swagger documentation for all endpoints
- [ ] Updated README with new API reference and examples
- [ ] Error handling standardization across all services

---

## 📋 Issue Backlog (Organized by Priority)

### 🔴 High Priority — MVP Essential

| # | Issue | Sprint | Service |
|---|-------|--------|---------|
| 1 | Rename Media entity to Book with book-specific fields | Sprint 3 | Catalog |
| 2 | Implement book submission with PENDING approval status | Sprint 3 | Catalog |
| 3 | Implement admin approval/rejection workflow | Sprint 3 | Catalog |
| 4 | Implement star rating system (0–5) | Sprint 4 | Engagement |
| 5 | Implement reading status tracking (TO_READ, READING, COMPLETED) | Sprint 4 | Engagement |
| 6 | Implement page progress tracking for READING status | Sprint 4 | Engagement |
| 7 | Adapt recommendation engine for book ratings and reading status | Sprint 5 | Recommendation |
| 8 | Paginated aggregator for book recommendations | Sprint 5 | Recommendation |

### 🟡 Medium Priority — MVP Important

| # | Issue | Sprint | Service |
|---|-------|--------|---------|
| 9 | Update Kafka topics for book events | Sprint 3 | All |
| 10 | Book search/filter by genre, author, title | Sprint 3 | Catalog |
| 11 | User reading list endpoint | Sprint 4 | Engagement |
| 12 | Aggregate rating display per book | Sprint 4 | Catalog |
| 13 | Standardized error handling across services | Sprint 6 | All |
| 14 | OpenAPI/Swagger documentation | Sprint 6 | All |

### 🟢 Low Priority — MVP Nice to Have

| # | Issue | Sprint | Service |
|---|-------|--------|---------|
| 15 | Redis caching for popular book recommendations | Sprint 6 | Recommendation |
| 16 | Health check endpoints (actuator) | Sprint 6 | All |
| 17 | CI/CD pipeline with GitHub Actions | Sprint 6 | Infrastructure |
| 18 | Rate limiting for API endpoints | Sprint 6 | All |

---

## 🚀 Future Features (Post-MVP)

### Phase 2.0: Group Reading & Social Features
- [ ] **Group Reading Chats:** Create reading groups for collaborative discussions on books
- [ ] **Group Book Clubs:** Organize scheduled reading sessions with shared reading lists
- [ ] **Activity Feed:** See what friends are reading and rating

### Phase 2.1: Intelligent Book Approval
- [ ] **Algorithmic Approval System:** Automated book approval based on:
  - ISBN validation against external databases
  - Duplicate detection using title/author similarity
  - Cover image validation
  - Metadata completeness scoring
- [ ] **Community Voting:** Allow trusted users to vote on pending submissions

### Phase 2.2: User Levels & Premium Features
- [ ] **Normal User:**
  - Can rate books (star ratings)
  - Can track reading status
  - Can join reading groups
  - Can receive recommendations
- [ ] **Premium User:**
  - All Normal features
  - Can upload custom book covers
  - Can create and manage reading groups
  - Can access advanced recommendation filters
  - Priority in book submission approval queue

### Phase 3: Advanced Recommendations
- [ ] **Collaborative Filtering:** Recommendations based on similar readers
- [ ] **Embedding Upgrades:** Spring AI with embedding models for richer book vectors
- [ ] **Real-time Learning:** Adaptive recommendations from immediate feedback
- [ ] **Diversity Enhancement:** Avoid genre filter bubbles

### Phase 4: Frontend
- [ ] **Web Application:** React / Next.js
- [ ] **Mobile App:** React Native or Flutter
- [ ] **Design System:** Reusable component library
- [ ] **PWA Support:** Offline reading list access

### Phase 5: Scalability & DevOps
- [ ] **Kubernetes:** Container orchestration
- [ ] **Full CI/CD:** GitHub Actions pipelines
- [ ] **Monitoring Stack:** Prometheus + Grafana
- [ ] **Distributed Tracing:** Jaeger / Zipkin
- [ ] **Log Aggregation:** ELK Stack

---

## 📁 Current Project Structure

```
media-recommendation-system/
├── docker-compose.yml                 # Service orchestration
├── PROJECT_ANALYSIS.md                # This document
├── README.md                          # Project documentation
├── scripts/
│   └── create-vector-in-recommendation-db.sql  # pgvector initialization
│
├── catalog-service/                   # Book Catalog (Hexagonal Architecture)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/mrs/catalog_service/
│       ├── application/               # Controllers, DTOs, Mappers
│       ├── domain/                    # Entities, Services, Handlers, Ports, Events
│       └── infrastructure/            # Persistence, Kafka Producers, Security
│
├── user-service/                      # User Management (Layered Architecture)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/mrs/user_service/
│       ├── module/                    # User, Auth, User Preference modules
│       └── share/                     # Config, Security, Validators
│
├── engagement-service/                # Reading & Rating Service (Hexagonal Architecture)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/mrs/engagement_service/
│       ├── application/               # Controllers, DTOs, Mappers
│       ├── domain/                    # Entities, Services, Handlers
│       └── infrastructure/            # Repositories, Kafka, Security
│
└── recommendation-service/            # Book Recommendation Aggregator (Hexagonal + pgvector)
    ├── Dockerfile
    ├── pom.xml
    └── src/main/java/com/mrs/recommendation_service/
        ├── application/               # Controllers, Kafka Consumers, DTOs
        ├── domain/                    # Entities, Handlers, Commands
        └── infrastructure/            # JPA Repositories, OpenFeign Client
```

---

## 🔧 Technology Stack

| Category | Technology | Version | Status |
|----------|------------|---------|--------|
| **Runtime** | Java | 21 (LTS) | ✅ |
| **Framework** | Spring Boot | 4.0.2 | ✅ |
| **Database** | PostgreSQL | 15 | ✅ |
| **Vector Search** | pgvector | — | ✅ |
| **Message Broker** | Apache Kafka | 7.3.0 | ✅ |
| **Coordination** | Apache Zookeeper | 7.3.0 | ✅ |
| **Containerization** | Docker + Compose | Latest | ✅ |
| **Security** | Spring Security + JWT | — | ✅ |
| **ORM** | Spring Data JPA / Hibernate | — | ✅ |
| **Inter-Service Calls** | Spring Cloud OpenFeign | — | ✅ |
| **Build** | Maven | — | ✅ |

---

## 📝 Final Notes

### Project Strengths
1. **Solid Architecture:** Microservices with hexagonal architecture and clear separation
2. **Infrastructure Ready:** Docker Compose for straightforward development
3. **Native Vector Search:** pgvector eliminates external ML dependencies
4. **Real-Time Updates:** Kafka-driven profile updates keep recommendations fresh
5. **Security Implemented:** JWT with role-based access across all services

### Priority Improvement Areas
1. **Book Domain Refactoring:** Rename and adapt entities from generic media to books
2. **Tests:** Nearly nonexistent — critical risk
3. **API Documentation:** No Swagger/OpenAPI
4. **New Interaction Models:** Star ratings and reading status need implementation

### Immediate Next Steps
1. 🔄 **Sprint 3:** Transform catalog to book-specific with admin approval
2. ⏳ **Sprint 4:** Implement star ratings and reading status
3. ⏳ **Sprint 5:** Adapt recommendation engine for books
4. ⏳ **Sprint 6:** Testing, documentation, and polish

---

*Last updated: February 2026*
