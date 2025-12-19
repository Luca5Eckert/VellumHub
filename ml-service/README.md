# ML Service - Media Recommendation System

## Overview

The ML Service is a **Python microservice** that calculates personalized media recommendations. It receives user profile data via REST API and fetches media features from the **recommendation_db** database.

## Architecture

### Hybrid Design
- **Receives UserProfile via API** - avoids large payloads in request body
- **Fetches MediaFeatures from recommendation_db** - single database connection
- **Calculates and returns recommendations** - hybrid algorithm with scoring
- **Follows database-per-service** - only accesses recommendation_db

### Integration Flow

```
recommendation-service (Java)
    ↓
    1. Calls ML Service API with UserProfile only
    ↓
ml-service (Python)
    ↓
    2. Fetches MediaFeatures from recommendation_db
    3. Calculates recommendations (content-based + popularity)
    4. Returns scored media list
    ↓
recommendation-service
    5. Stores/returns recommendations
```

## Features

- **Efficient API Design**: Small request payload (only UserProfile)
- **Database Access**: Direct access to recommendation_db for MediaFeatures
- **Hybrid Algorithm**: Content-based filtering (70%) + popularity boost (30%)
- **Performance**: <100ms processing time per request
- **Scalable**: Horizontally scalable with connection pooling
- **Production Ready**: Docker, Gunicorn, health checks

## API Endpoint

### POST /api/recommendations

Calculate personalized recommendations based on user profile.
ML service fetches media features from database.

**Request Body:**
```json
{
  "user_profile": {
    "user_id": "uuid",
    "genre_scores": {
      "ACTION": 5.0,
      "THRILLER": 3.0,
      "HORROR": 2.0
    },
    "interacted_media_ids": ["uuid1", "uuid2"],
    "total_engagement_score": 100.0
  },
  "limit": 10
}
```

**Response:**
```json
{
  "user_id": "uuid",
  "recommendations": [
    {
      "media_id": "uuid",
      "genres": ["ACTION", "THRILLER"],
      "popularity_score": 0.8,
      "recommendation_score": 0.8745,
      "content_score": 0.8500
    }
  ],
  "count": 10
}
```

### GET /health

Health check endpoint.

**Response:**
```json
{
  "status": "healthy",
  "service": "ml-service",
  "version": "2.0.0"
}
```

## Algorithm Details

### Hybrid Recommendation Algorithm

**Weights:**
- Content-Based Filtering: 70%
- Popularity Boost: 30%

**Content-Based Scoring:**
```python
# Match media genres to user preferences
for each media:
    matching_scores = [genre_scores[g] for g in media.genres if g in genre_scores]
    avg_score = sum(matching_scores) / len(matching_scores)
    normalized = avg_score / 10.0  # Normalize to 0-1
    
    # Boost for multiple matches
    match_ratio = len(matching_scores) / len(media.genres)
    content_score = normalized * (0.8 + 0.2 * match_ratio)
```

**Final Score:**
```python
recommendation_score = (content_score × 0.7) + (popularity_score × 0.3)
```

## Database Schema

The ML service accesses the `medias_features` table in recommendation_db:

```sql
CREATE TABLE medias_features (
    media_id UUID PRIMARY KEY,
    genres TEXT[],
    popularity_score DOUBLE PRECISION
);
```

## Running the Service

### Development

```bash
cd ml-service
pip install -r requirements.txt

# Set environment variables
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=recommendation_db
export DB_USER=user
export DB_PASSWORD=password

python app.py
```

### Production with Docker

```bash
docker-compose up ml-service
```

### Testing

```bash
# Health check
curl http://localhost:5000/health

# Calculate recommendations
curl -X POST http://localhost:5000/api/recommendations \
  -H "Content-Type: application/json" \
  -d '{
    "user_profile": {
      "user_id": "123e4567-e89b-12d3-a456-426614174000",
      "genre_scores": {"ACTION": 5.0, "THRILLER": 3.0},
      "interacted_media_ids": []
    },
    "limit": 10
  }'
```

## Dependencies

- Flask 3.0.0 - Web framework
- Flask-CORS 4.0.0 - CORS support
- psycopg2-binary 2.9.9 - PostgreSQL adapter
- python-dotenv 1.0.0 - Environment variables
- gunicorn 21.2.0 - WSGI server
- requests 2.31.0 - HTTP library (for health checks)

## Environment Variables

```env
# Database (only recommendation_db)
DB_HOST=localhost
DB_PORT=5432
DB_NAME=recommendation_db
DB_USER=user
DB_PASSWORD=password

# Service
PORT=5000
DEBUG=False
MAX_RECOMMENDATIONS_LIMIT=100
LOG_LEVEL=INFO
```

## Performance Characteristics

- **Processing Time**: <100ms per request (including DB query)
- **Concurrent Workers**: 4 workers × 2 threads = 8
- **Database**: Connection pool (2-10 connections)
- **Memory**: ~150MB per worker
- **Scalability**: Horizontal scaling with shared database

## Integration Example (Java)

```java
// From recommendation-service
RestTemplate restTemplate = new RestTemplate();

// Prepare request (only UserProfile, no media list)
RecommendationRequest request = new RecommendationRequest();
request.setUserProfile(userProfile);
request.setLimit(10);

// Call ML service - it will fetch media from DB
ResponseEntity<RecommendationResponse> response = restTemplate.postForEntity(
    "http://ml-service:5000/api/recommendations",
    request,
    RecommendationResponse.class
);

List<ScoredMedia> recommendations = response.getBody().getRecommendations();
```

## Design Decisions

### Why Database Access?

**Problem with pure stateless:** Sending thousands of media features in request body creates:
- Large network payloads (MBs of data)
- Slow request/response times
- Network bandwidth waste
- Potential timeout issues

**Solution with DB access:**
- ✅ Small request payload (only UserProfile ~1KB)
- ✅ Fast network transfer
- ✅ ML service fetches only needed data from DB
- ✅ Still follows database-per-service (only recommendation_db)
- ✅ Scalable with connection pooling

### Why recommendation_db?

The `medias_features` table in recommendation_db is a **materialized view** that:
- Contains denormalized media data for recommendation purposes
- Is owned by recommendation-service domain
- Is updated when media catalog changes
- Optimized for ML service queries

## Future Enhancements

1. **Caching**: Add Redis for frequently requested users
2. **Advanced ML**: Train collaborative filtering models
3. **Real-time Updates**: Stream media feature updates
4. **A/B Testing**: Test different algorithm weights
5. **Batch Processing**: Pre-compute recommendations for popular profiles

## License

Part of the Media Recommendation System - MIT License
