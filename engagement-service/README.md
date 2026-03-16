# ⭐ Engagement Service

The Engagement Service manages user interactions with books, specifically handling ratings and reviews. It tracks user engagement metrics that power VellumHub's recommendation engine.

## 🎯 Overview

This service enables users to rate books on a 0-5 star scale with optional text reviews. Ratings are published to Kafka to update user profiles in the Recommendation Service, creating a feedback loop for personalized suggestions.

**Port:** `8083`  
**Database:** `engagement_db` (PostgreSQL)

---

## 📋 Features

### 1. **Star Ratings** ⭐
- Rate books from 0 to 5 stars
- Optional text reviews (up to 1000 characters)
- One rating per user per book (update existing to change)
- Timestamp tracking for rating history

### 2. **Rating Retrieval** 📊
- Get all ratings by a specific user
- Get all ratings for a specific book
- Get authenticated user's own ratings
- Advanced filtering by star range and date range

### 3. **Rating Management** ✏️
- Update existing ratings
- Delete ratings
- Duplicate prevention
- Star validation (0-5 range)

### 4. **Event-Driven Updates** 🔄
- Publishes rating events to Kafka
- Enables real-time recommendation profile updates
- Asynchronous processing for better performance

---

## 🏗️ Architecture

### Module Structure

#### **rating** Module
- **Controller:** `RatingController`
- **Handlers:** 
  - CreateRating
  - GetUserRating
  - UpdateRating
  - DeleteRating
  - GetAllRatingByBookId
- **Use Cases:** 5 business operations for rating management
- **Producers:** `CreatedRatingEventProducer` (Kafka)
- **Filters:** `RatingFilterProvider` for complex queries

### Architecture Patterns

- **DDD (Domain-Driven Design):** Commands, use cases, domain models
- **Event-Driven:** Publishes events for recommendation engine
- **Filter Provider Pattern:** Abstraction for complex rating queries
- **Layered Architecture:** Controller → Use Case → Handler → Repository

---

## 🔌 API Endpoints

### Create Rating

```http
POST /rating
```

**Request Body:**
```json
{
  "bookId": "123e4567-e89b-12d3-a456-426614174000",
  "stars": 5,
  "review": "Amazing book! Highly recommended."
}
```

**Validation:**
- `stars`: Required, must be between 0 and 5
- `review`: Optional, max 1000 characters
- `bookId`: Required, must be valid UUID

**Response:** `201 Created`

---

### Get User's Ratings

```http
GET /rating/{userId}
```

**Query Parameters:**
- `minStars` (optional) - Filter by minimum stars (0-5)
- `maxStars` (optional) - Filter by maximum stars (0-5)
- `from` (optional) - Filter by start date (ISO 8601: `yyyy-MM-dd'T'HH:mm:ss`)
- `to` (optional) - Filter by end date (ISO 8601)
- `pageNumber` (optional) - Page number (default: 0)
- `pageSize` (optional) - Page size (default: 10)

**Example:**
```
GET /rating/123e4567-e89b-12d3-a456-426614174000?minStars=4&maxStars=5&pageNumber=0&pageSize=20
```

---

### Get Own Ratings

```http
GET /rating/me
```

Same query parameters as above. User ID is extracted from JWT token.

---

### Get Ratings for a Book

```http
GET /rating/{bookId}
```

**Query Parameters:**
- `pageNumber` (optional) - Page number (default: 0)
- `pageSize` (optional) - Page size (default: 10)

Returns all ratings for the specified book with pagination.

---

### Update Rating

```http
PUT /rating/{ratingId}
```

**Request Body:**
```json
{
  "stars": 4,
  "review": "Updated review text"
}
```

**Authorization:** Only the user who created the rating can update it.

---

### Delete Rating

```http
DELETE /rating/{ratingId}
```

**Authorization:** Only the user who created the rating can delete it.

---

## 📊 Database Schema

### rating Table

- `id` (Long, PK) - Unique rating identifier (auto-generated)
- `user_id` (UUID, FK) - User who created the rating
- `book_id` (UUID, FK) - Book being rated
- `stars` (int) - Rating score (0-5)
- `review` (String) - Optional text review (max 1000 chars)
- `timestamp` (LocalDateTime) - When the rating was created/updated

### Constraints

- **Unique constraint:** One rating per user per book (`user_id`, `book_id`)
- **Check constraint:** `stars` must be between 0 and 5

---

## 🔄 Event Publishing

The service publishes events to Kafka for inter-service communication:

### Produced Events

**Topic:** `rating-events`

**Event:** `CreatedRatingEvent`
```json
{
  "userId": "123e4567-e89b-12d3-a456-426614174000",
  "bookId": "123e4567-e89b-12d3-a456-426614174001",
  "stars": 5,
  "timestamp": "2024-03-15T10:30:00"
}
```

**When Published:**
- On rating creation (POST)
- On rating update (PUT)

**Consumers:**
- **Recommendation Service** - Updates user profile vectors based on ratings

---

## 🛠️ Technology Stack

- **Java 21**
- **Spring Boot 3.4.2**
- **Spring Data JPA** - Database access
- **PostgreSQL** - Relational database
- **Apache Kafka** - Event streaming
- **OpenAPI 3.0 (Swagger)** - API documentation
- **Maven** - Build tool

---

## 🚀 Running the Service

### Standalone (Development)

```bash
cd engagement-service
./mvnw spring-boot:run
```

The service will start on port `8083`.

### With Docker Compose

From the project root:

```bash
docker-compose up engagement-service
```

### Environment Variables

Required configuration (typically in `.env` or `application.yml`):

```properties
# Database
POSTGRES_USER=admin
POSTGRES_PASSWORD=your-password

# Service Port
SERVER_PORT=8083

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Security (JWT validation)
JWT_KEY=your-secret-key
```

---

## 📝 API Documentation

When the service is running, access the Swagger UI at:

```
http://localhost:8083/swagger-ui.html
```

---

## 🔐 Security

### Authentication

All endpoints require JWT authentication via Bearer token:

```
Authorization: Bearer <jwt-token>
```

### Authorization

- **Create Rating:** Any authenticated user
- **Get Ratings:** Any authenticated user
- **Update Rating:** Only the user who created it
- **Delete Rating:** Only the user who created it

User ID is automatically extracted from the JWT token for security.

---

## 🧪 Testing

Run the test suite:

```bash
./mvnw test
```

The service includes unit tests using:
- JUnit 5
- Mockito
- Spring Boot Test

---

## 📈 Monitoring

### Health Check

```bash
curl http://localhost:8083/actuator/health
```

### Metrics

```bash
curl http://localhost:8083/actuator/metrics
```

---

## 🔍 Common Use Cases

### 1. Rate a Book

```bash
curl -X POST http://localhost:8083/rating \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "bookId": "123e4567-e89b-12d3-a456-426614174000",
    "stars": 5,
    "review": "Absolutely loved this book!"
  }'
```

### 2. Get My Ratings (4-5 Stars Only)

```bash
curl -X GET "http://localhost:8083/rating/me?minStars=4&maxStars=5" \
  -H "Authorization: Bearer <token>"
```

### 3. Get All Ratings for a Book

```bash
curl -X GET "http://localhost:8083/rating/{bookId}?pageNumber=0&pageSize=20" \
  -H "Authorization: Bearer <token>"
```

### 4. Update a Rating

```bash
curl -X PUT http://localhost:8083/rating/123 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "stars": 4,
    "review": "Still great, but not perfect."
  }'
```

### 5. Delete a Rating

```bash
curl -X DELETE http://localhost:8083/rating/123 \
  -H "Authorization: Bearer <token>"
```

---

## 📊 Use Case: Rating Analysis

### Get High-Rated Books

Query the database to find books with average ratings above 4 stars:

```sql
SELECT book_id, AVG(stars) as avg_rating, COUNT(*) as rating_count
FROM rating
GROUP BY book_id
HAVING AVG(stars) >= 4.0
ORDER BY avg_rating DESC, rating_count DESC
LIMIT 20;
```

### Get Most Active Reviewers

Find users who have written the most reviews:

```sql
SELECT user_id, COUNT(*) as review_count
FROM rating
WHERE review IS NOT NULL AND LENGTH(review) > 0
GROUP BY user_id
ORDER BY review_count DESC
LIMIT 20;
```

---

## 🤝 Contributing

This service follows established patterns:

1. **Controllers** handle HTTP requests and validation
2. **Use Cases** represent business operations
3. **Handlers** contain domain logic
4. **Repositories** abstract data access
5. **Event Producers** publish to Kafka

When adding features:
- Follow the existing module structure
- Use DTOs for API contracts
- Implement proper validation
- Add unit tests
- Update this README

---

## 📚 Related Services

- **User Service** - Provides user authentication
- **Catalog Service** - Provides book information
- **Recommendation Service** - Consumes rating events to update user profiles

---

## 🐛 Troubleshooting

### "Duplicate rating" Error

Each user can only rate a book once. Use PUT to update an existing rating instead.

### Invalid Star Rating

Stars must be an integer between 0 and 5 (inclusive).

### "Unauthorized to modify rating" Error

You can only update or delete your own ratings. The user ID is extracted from the JWT token and must match the rating's owner.

### Rating Event Not Published

Check:
- Kafka broker is running and accessible
- Topic `rating-events` exists
- Service has correct Kafka configuration

---

## 📈 Performance Considerations

### Indexing

The database should have indexes on:
- `user_id` - For user rating queries
- `book_id` - For book rating queries
- `timestamp` - For date range filtering
- `(user_id, book_id)` - For duplicate checking

### Pagination

Always use pagination when retrieving ratings to avoid large result sets:
- Default page size: 10
- Maximum recommended page size: 100

### Event Publishing

Rating events are published asynchronously to avoid blocking the API response. This provides:
- Better response times
- Resilience to Kafka outages (with proper error handling)
- Decoupling from recommendation service

---

**Engagement Service** - Capturing what readers think 💭⭐
