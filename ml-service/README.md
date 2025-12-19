# ML Service - Media Recommendation System

## Overview

The ML Service is a **stateless Python microservice** that calculates personalized media recommendations. It receives user profile data and available media via REST API, applies a hybrid recommendation algorithm, and returns scored recommendations.

## Architecture

### Stateless Design
- **No database connections** - follows database-per-service pattern
- Receives all data via API requests
- Pure computation service focused on recommendation algorithms
- Can be horizontally scaled without coordination

### Integration Flow

```
recommendation-service (Java)
    ↓
    1. Fetches UserProfile from recommendation_db
    2. Fetches MediaFeatures from recommendation_db  
    3. Calls ML Service API with data
    ↓
ml-service (Python)
    ↓
    Calculates recommendations (stateless)
    ↓
    Returns scored media list
    ↓
recommendation-service
    Stores/returns recommendations
```

## Features

- **Stateless Architecture**: No database dependencies, pure computation
- **Hybrid Algorithm**: Content-based filtering with popularity boost
- **Performance**: <50ms processing time per request
- **Scalable**: Horizontally scalable, stateless design
- **Production Ready**: Docker, Gunicorn, health checks

## API Endpoint

### POST /api/recommendations

Calculate personalized recommendations based on user profile and available media.

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
  "available_media": [
    {
      "media_id": "uuid",
      "genres": ["ACTION", "THRILLER"],
      "popularity_score": 0.8,
      "title": "Inception",
      "description": "A mind-bending thriller...",
      "release_year": 2010,
      "media_type": "MOVIE",
      "cover_url": "https://..."
    }
  ],
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
      "title": "Inception",
      "genres": ["ACTION", "THRILLER"],
      "popularity_score": 0.8,
      "release_year": 2010,
      "media_type": "MOVIE",
      "cover_url": "https://...",
      "recommendation_score": 0.8745,
      "content_score": 0.8500,
      "popularity_score": 0.8000
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

## Running the Service

### Development

```bash
cd ml-service
pip install -r requirements.txt
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
      "genre_scores": {"ACTION": 5.0, "THRILLER": 3.0}
    },
    "available_media": [
      {
        "media_id": "uuid",
        "genres": ["ACTION"],
        "popularity_score": 0.8,
        "title": "Action Movie"
      }
    ],
    "limit": 10
  }'
```

## Dependencies

- Flask 3.0.0 - Web framework
- Flask-CORS 4.0.0 - CORS support
- python-dotenv 1.0.0 - Environment variables
- gunicorn 21.2.0 - WSGI server
- requests 2.31.0 - HTTP library (for health checks)

## Environment Variables

```env
PORT=5000
DEBUG=False
LOG_LEVEL=INFO
```

## Performance Characteristics

- **Processing Time**: <50ms per request
- **Concurrent Workers**: 4 workers × 2 threads = 8
- **Stateless**: No shared state, perfect for horizontal scaling
- **Memory**: Low memory footprint (~100MB per worker)

## Integration Example (Java)

```java
// From recommendation-service
RestTemplate restTemplate = new RestTemplate();

// Prepare request
RecommendationRequest request = new RecommendationRequest();
request.setUserProfile(userProfile);
request.setAvailableMedia(mediaFeatures);
request.setLimit(10);

// Call ML service
ResponseEntity<RecommendationResponse> response = restTemplate.postForEntity(
    "http://ml-service:5000/api/recommendations",
    request,
    RecommendationResponse.class
);

List<ScoredMedia> recommendations = response.getBody().getRecommendations();
```

## Design Decisions

### Why Stateless?

1. **Database per Service**: Follows microservices best practices
2. **Scalability**: Can scale horizontally without coordination
3. **Simplicity**: No database connection management
4. **Performance**: Pure computation without I/O overhead
5. **Testing**: Easy to test with different inputs

### Why Simple Algorithm?

For MVP, we prioritize:
- **Fast implementation** and deployment
- **Explainable** recommendations
- **Good enough** accuracy for initial users
- Foundation for **future ML models**

### Future Enhancements

1. **Advanced ML Models**: Train models on historical data
2. **Collaborative Filtering**: Use user similarity
3. **Real-time Learning**: Update based on feedback
4. **A/B Testing**: Compare algorithm variations
5. **Caching**: Add Redis for frequently requested users

## License

Part of the Media Recommendation System - MIT License
