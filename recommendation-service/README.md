# 🤖 Recommendation Service

The Recommendation Service is the AI brain of VellumHub, providing personalized book recommendations using vector similarity search and collaborative filtering. It leverages PostgreSQL with the pgvector extension for efficient similarity matching.

## 🎯 Overview

This service maintains user preference profiles and book feature embeddings, using them to generate intelligent recommendations. It processes rating events and book catalog changes in real-time to keep recommendations fresh and relevant.

**Port:** `8085`  
**Database:** `recommendation_db` (PostgreSQL + pgvector extension)

---

## 📋 Features

### 1. **Personalized Recommendations** 🎯
- Vector similarity-based matching
- Genre-aware recommendations
- Collaborative filtering from rating history
- Popularity-weighted suggestions
- Configurable result limits and offsets

### 2. **User Profile Learning** 📚
- Dynamic preference vectors (384-dimensional)
- Learns from rating behavior
- Positive reinforcement for high ratings (4-5 stars)
- Negative adjustments for low ratings (1-2 stars)
- Engagement score tracking

### 3. **Book Feature Management** 📊
- AI-generated semantic vector embeddings
- Popularity score calculation
- Real-time updates via Kafka events
- Synchronized with catalog service

### 4. **Event-Driven Architecture** 🔄
- Consumes book events from Catalog Service
- Consumes rating events from Engagement Service
- Asynchronous profile updates
- Eventual consistency model

---

## 🏗️ Architecture

### Modules

#### **recommendation** Module
- **Controller:** `RecommendationController`
- **Handler:** `GetRecommendationsHandler`
- **Use Case:** `GetRecommendationsUseCase`
- **Features:**
  - Calculates vector similarity scores
  - Enriches results with book details and covers
  - Filters out already-rated books
  - Ranks by similarity + popularity

#### **book_feature** Module
- **Kafka Consumers:**
  - `CreateBookConsumerEvent`
  - `UpdateBookConsumerEvent`
  - `DeleteBookConsumerEvent`
- **Use Cases:**
  - CreateBookFeature
  - UpdateMediaFeature
  - DeleteBookFeature
- **Features:**
  - 384-dimensional semantic embeddings
  - Popularity score calculation
  - Feign client integration with Catalog Service

#### **user_profile** Module
- **Kafka Consumer:** `CreatedRatingConsumerEvent`
- **Handler:** `CreatedRatingConsumerHandler`
- **Use Case:** `UpdateUserProfileWithRatingUseCase`
- **Features:**
  - Profile vector learning with adjustment rates
  - Tracks interacted books
  - Cumulative engagement scoring

### Architecture Patterns

- **Vector Similarity Search:** Cosine distance in 384D space
- **Event-Driven:** Kafka consumers for async updates
- **Event-Carried State Transfer (ECST):** Events carry book/rating state to keep `book_features` and `user_profiles` current
- **Microservice Integration:** Feign clients for service communication
- **CQRS:** Separation of profile updates and recommendation reads

---

## 🔌 API Endpoints

### Get Recommendations

```http
GET /api/recommendations
```

**Query Parameters:**
- `limit` (optional) - Number of recommendations to return (default: 10)
- `offset` (optional) - Number of recommendations to skip (default: 0)

**Headers:**
```
Authorization: Bearer <jwt-token>
```

**Response:**
```json
{
  "recommendations": [
    {
      "bookId": "123e4567-e89b-12d3-a456-426614174000",
      "title": "The Great Gatsby",
      "author": "F. Scott Fitzgerald",
      "description": "A story about...",
      "genres": ["FICTION", "CLASSIC"],
      "similarityScore": 0.89,
      "popularityScore": 4.5,
      "coverImage": "base64-encoded-image..."
    },
    // ... more recommendations
  ],
  "totalRecommendations": 10,
  "hasMore": true
}
```

**How It Works:**
1. Retrieves user profile vector from database
2. Fetches book features (embeddings + popularity)
3. Calculates similarity scores (vector distance)
4. Filters out books the user has already rated
5. Ranks by combined similarity + popularity score
6. Enriches with full book details from Catalog Service
7. Returns top N results with pagination

---

## 📊 Database Schema

### book_features Table

Stores vector embeddings and metadata for each book.

- `book_id` (UUID, PK) - Book identifier (from catalog)
- `embedding` (float[384]) - Semantic vector embedding
- `popularity_score` (double) - Book popularity metric
- `last_updated` (Instant) - Last sync timestamp

**Embedding Structure:**
- 384-dimensional semantic vector generated from title, author, description, and genres
- Text normalization is applied before embedding generation
- Stored in pgvector for cosine similarity search

### user_profiles Table

Stores learned preference vectors for each user.

- `user_id` (UUID, PK) - User identifier
- `profile_vector` (float[384]) - Learned preference embedding
- `interacted_book_ids` (UUID[], array) - Books user has rated
- `total_engagement_score` (double) - Cumulative engagement metric
- `created_at` (Instant) - Profile creation time
- `last_updated` (Instant) - Last update timestamp

**Profile Vector Learning:**
- Initialized with neutral values
- Updated based on rating behavior:
  - 5 stars: Strong positive adjustment toward the rated book embedding
  - 4 stars: Moderate positive adjustment toward the rated book embedding
  - 3 stars: Minimal adjustment
  - 1-2 stars: Negative adjustment away from the rated book embedding

---

## 🔄 Event Consumption

The service consumes events from Kafka to maintain data consistency:
These events implement **Event-Carried State Transfer**, ensuring recommendation state stays local and up to date via incremental updates.

### Consumed Events

#### From Catalog Service (Topics: `created-book`, `updated-book`, `deleted-book`)

**CreateBookEvent (`created-book`):**
- Creates new book feature
- Generates semantic embedding
- Initializes popularity score

**UpdateBookEvent (`updated-book`):**
- Updates existing book feature
- Recalculates embedding when metadata changes
- Updates metadata

**DeleteBookEvent (`deleted-book`):**
- Removes book feature from database
- Cleans up recommendation data

#### From Engagement Service (Topic: `created-rating`)

**CreatedRatingEvent:**
- Updates user profile vector
- Adds book to interacted list
- Adjusts engagement score
- Applies rating weight to preference learning

---

## 🧮 Recommendation Algorithm

### 1. Vector Similarity Calculation

Uses **cosine similarity** between user profile and book embeddings:

```
similarity = (userVector · bookVector) / (||userVector|| × ||bookVector||)
```

### 2. Semantic Embedding

Books are represented as 384-dimensional semantic vectors generated from normalized textual content.
The embedding payload includes title, author, description, and genre labels.

### 3. User Profile Learning

User profiles evolve based on ratings:

```java
if (stars >= 4) {
  profileVector += bookEmbedding × adjustmentRate × (stars / 5.0)
} else if (stars <= 2) {
  profileVector -= bookEmbedding × adjustmentRate × (3 - stars) / 5.0
}
```

### 4. Ranking Function

Final score combines similarity and popularity:

```
finalScore = (similarity × 0.7) + (normalizedPopularity × 0.3)
```

### 5. Filtering

- Excludes books user has already rated
- Ensures diversity in recommendations
- Applies minimum similarity threshold

---

## 🛠️ Technology Stack

- **Java 21**
- **Spring Boot 3.4.2**
- **Spring Data JPA** - Database access
- **PostgreSQL 15** - Relational database
- **pgvector Extension** - Vector similarity operations
- **Apache Kafka** - Event streaming
- **OpenFeign** - HTTP client for microservice communication
- **OpenAPI 3.0** - API documentation
- **Maven** - Build tool

---

## 🚀 Running the Service

### Standalone (Development)

```bash
cd recommendation-service
./mvnw spring-boot:run
```

The service will start on port `8085`.

### With Docker Compose

From the project root:

```bash
docker-compose up recommendation-service
```

### Environment Variables

Required configuration (typically in `.env` or `application.yml`):

```properties
# Database
POSTGRES_USER=admin
POSTGRES_PASSWORD=your-password

# Service Port
SERVER_PORT=8085

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Security (JWT validation)
JWT_KEY=your-secret-key

# Catalog Service URL (for Feign client)
CATALOG_SERVICE_URL=http://catalog-service:8081
```

### Database Setup

The PostgreSQL database must have the **pgvector** extension enabled:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

This is automatically handled by the initialization script:
```bash
scripts/create-vector-in-recommendation-db.sql
```

---

## 📝 API Documentation

When the service is running, access the Swagger UI at:

```
http://localhost:8085/swagger-ui.html
```

---

## 🔐 Security

### Authentication

All endpoints require JWT authentication:

```
Authorization: Bearer <jwt-token>
```

User ID is automatically extracted from the token for personalized recommendations.

---

## 🧪 Testing

Run the test suite:

```bash
./mvnw test
```

The service includes unit tests for:
- Recommendation algorithm
- Vector operations
- Event consumers
- Profile updates

---

## 📈 Monitoring

### Health Check

```bash
curl http://localhost:8085/actuator/health
```

### Metrics

```bash
curl http://localhost:8085/actuator/metrics
```

### Kafka Consumer Monitoring

Monitor consumer lag via Kafka UI at `http://localhost:8090`:
- Check `recommendation-service` consumer group subscriptions for `created-book`, `updated-book`, and `deleted-book`
- Check `recommendation-service` consumer group subscription for `created-rating`
- Monitor message processing rates

---

## 🔍 Common Use Cases

### 1. Get Personalized Recommendations

```bash
curl -X GET "http://localhost:8085/api/recommendations?limit=10&offset=0" \
  -H "Authorization: Bearer <token>"
```

### 2. Query User Profile Vector

```sql
SELECT user_id, profile_vector, total_engagement_score, last_updated
FROM user_profiles
WHERE user_id = '123e4567-e89b-12d3-a456-426614174000';
```

### 3. Find Similar Books

```sql
SELECT book_id, embedding <=> :user_profile_vector::vector AS distance
FROM book_features
ORDER BY distance
LIMIT 10;
```

The `<=>` operator calculates cosine distance using pgvector.

### 4. Check Book Features

```sql
SELECT book_id, popularity_score, last_updated
FROM book_features
ORDER BY popularity_score DESC
LIMIT 20;
```

---

## 🎓 Understanding the Vector Space

### Semantic Dimensions (384D)

Each vector dimension is part of a dense semantic representation learned by the embedding model.
Dimensions are not one-hot genre slots; meaning emerges from the full vector pattern.

### Example Vectors

**User Profile (truncated example):**
```
[0.11, -0.03, 0.28, ..., 0.07]
```

**Book (truncated example):**
```
[0.10, -0.02, 0.31, ..., 0.05]
```

**Similarity:** High when cosine distance is low between user and book vectors.

---

## 🤝 Contributing

This service follows established patterns:

1. **Controllers** handle HTTP requests
2. **Use Cases** orchestrate recommendation logic
3. **Handlers** process events and update profiles
4. **Repositories** abstract database access
5. **Feign Clients** communicate with other services

When adding features:
- Consider vector dimensionality changes carefully
- Test similarity calculations thoroughly
- Monitor consumer lag and performance
- Update this README

---

## 📚 Related Services

- **Catalog Service** - Provides book details via Feign client, publishes book events
- **Engagement Service** - Publishes rating events for profile updates
- **User Service** - Provides user authentication

---

## 🐛 Troubleshooting

### No Recommendations Returned

Possible causes:
- User has no profile (hasn't rated any books yet)
- All books have been rated by user
- Database has no book features

**Solution:** Rate a few books to initialize the user profile.

### Stale Recommendations

Recommendations not updating after new ratings:
- Check Kafka consumer lag
- Verify rating events are being published
- Check consumer error logs

### pgvector Errors

If you see errors related to vector operations:
- Ensure pgvector extension is installed: `CREATE EXTENSION vector;`
- Verify vector dimensions match (384D)
- Check PostgreSQL version (requires 11+)

### Feign Client Errors

Cannot fetch book details:
- Verify Catalog Service is running
- Check network connectivity between services
- Verify Feign client configuration

---

## 📈 Performance Optimization

### Vector Index

Create an index for faster similarity searches:

```sql
CREATE INDEX ON book_features USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);
```

### Caching

Consider caching:
- User profiles (frequently accessed)
- Book features (relatively static)
- Popular recommendations (high traffic)

### Batch Processing

For efficiency:
- Bulk fetch book details from Catalog Service
- Use bulk cover retrieval endpoint
- Process rating events in batches

---

**Recommendation Service** - Helping readers discover their next favorite book 🔮📚
