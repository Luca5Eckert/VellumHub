#!/usr/bin/env python3
"""
Manual API test examples for ML Service (stateless version)
"""

import requests
import json
import sys


def test_health_check(base_url):
    """Test the health check endpoint"""
    print("\n" + "="*60)
    print("Testing Health Check Endpoint")
    print("="*60)
    
    try:
        response = requests.get(f"{base_url}/health", timeout=5)
        print(f"Status Code: {response.status_code}")
        print(f"Response: {json.dumps(response.json(), indent=2)}")
        return response.status_code == 200
    except Exception as e:
        print(f"Error: {e}")
        return False


def test_recommendations(base_url):
    """Test calculating recommendations with sample data"""
    print("\n" + "="*60)
    print("Testing Recommendations Endpoint")
    print("="*60)
    
    # Sample request data
    request_data = {
        "user_profile": {
            "user_id": "123e4567-e89b-12d3-a456-426614174000",
            "genre_scores": {
                "ACTION": 5.0,
                "THRILLER": 3.0,
                "HORROR": 2.0
            },
            "interacted_media_ids": [],
            "total_engagement_score": 100.0
        },
        "available_media": [
            {
                "media_id": "media-uuid-1",
                "genres": ["ACTION", "THRILLER"],
                "popularity_score": 0.8,
                "title": "Action Thriller Movie",
                "description": "An exciting action thriller",
                "release_year": 2023,
                "media_type": "MOVIE",
                "cover_url": "https://example.com/cover1.jpg"
            },
            {
                "media_id": "media-uuid-2",
                "genres": ["HORROR"],
                "popularity_score": 0.6,
                "title": "Horror Film",
                "description": "A scary horror movie",
                "release_year": 2022,
                "media_type": "MOVIE",
                "cover_url": "https://example.com/cover2.jpg"
            },
            {
                "media_id": "media-uuid-3",
                "genres": ["ACTION"],
                "popularity_score": 0.9,
                "title": "Pure Action",
                "description": "Non-stop action",
                "release_year": 2024,
                "media_type": "MOVIE",
                "cover_url": "https://example.com/cover3.jpg"
            }
        ],
        "limit": 10
    }
    
    try:
        response = requests.post(
            f"{base_url}/api/recommendations",
            json=request_data,
            headers={'Content-Type': 'application/json'},
            timeout=10
        )
        print(f"Status Code: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print(f"\nUser ID: {data.get('user_id')}")
            print(f"Count: {data.get('count')}")
            print(f"\nRecommendations:")
            
            for i, rec in enumerate(data.get('recommendations', []), 1):
                print(f"\n{i}. {rec.get('title')} ({rec.get('release_year')})")
                print(f"   Genres: {', '.join(rec.get('genres', []))}")
                print(f"   Recommendation Score: {rec.get('recommendation_score')}")
                print(f"   - Content: {rec.get('content_score')}")
                print(f"   - Popularity: {rec.get('popularity_score')}")
            
            return True
        else:
            print(f"Error Response: {response.text}")
            return False
            
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()
        return False


def main():
    """Run manual tests"""
    # Configuration
    base_url = "http://localhost:5000"
    
    print("="*60)
    print("ML Service - API Tests (Stateless Version)")
    print("="*60)
    print(f"\nBase URL: {base_url}")
    print("\nNote: This tests the stateless API with sample data")
    
    # Run tests
    results = []
    
    # Test 1: Health check
    results.append(("Health Check", test_health_check(base_url)))
    
    # Test 2: Recommendations with sample data
    results.append(("Calculate Recommendations", test_recommendations(base_url)))
    
    # Summary
    print("\n" + "="*60)
    print("Test Summary")
    print("="*60)
    for name, result in results:
        status = "✓ PASS" if result else "✗ FAIL"
        print(f"{status} - {name}")
    
    passed = sum(1 for _, r in results if r)
    print(f"\nResults: {passed}/{len(results)} tests passed")
    
    return 0 if passed > 0 else 1


if __name__ == '__main__':
    sys.exit(main())
