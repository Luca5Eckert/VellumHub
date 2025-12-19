#!/usr/bin/env python3
"""
Simple test script to validate ML Service structure
"""

import sys

def test_imports():
    """Test that all required modules can be imported"""
    print("Testing imports...")
    try:
        import flask
        import flask_cors
        print("✓ All required packages can be imported")
        return True
    except ImportError as e:
        print(f"✗ Import error: {e}")
        return False


def test_app_structure():
    """Test that the Flask app can be created"""
    print("\nTesting app structure...")
    try:
        import os
        os.environ['PORT'] = '5000'
        
        # Import modules
        from services.recommendation_engine import RecommendationEngine
        
        print("✓ All modules imported successfully")
        return True
    except Exception as e:
        print(f"✗ Structure test error: {e}")
        return False


def test_recommendation_engine():
    """Test that the recommendation engine works"""
    print("\nTesting recommendation engine...")
    try:
        from services.recommendation_engine import RecommendationEngine
        
        engine = RecommendationEngine()
        
        # Test with sample data
        user_profile = {
            'user_id': 'test-uuid',
            'genre_scores': {'ACTION': 5.0, 'THRILLER': 3.0},
            'interacted_media_ids': []
        }
        
        available_media = [
            {
                'media_id': 'media-1',
                'genres': ['ACTION', 'THRILLER'],
                'popularity_score': 0.8,
                'title': 'Test Movie'
            }
        ]
        
        recommendations = engine.calculate_recommendations(
            user_profile=user_profile,
            available_media=available_media,
            limit=10
        )
        
        if len(recommendations) > 0:
            print(f"✓ Generated {len(recommendations)} recommendations")
            return True
        else:
            print("✗ No recommendations generated")
            return False
            
    except Exception as e:
        print(f"✗ Recommendation engine test error: {e}")
        import traceback
        traceback.print_exc()
        return False


def test_api_endpoints():
    """Test that Flask routes are properly defined"""
    print("\nTesting API endpoints...")
    try:
        import os
        os.environ['PORT'] = '5000'
        
        from app import app
        
        # Check that routes exist
        routes = [rule.rule for rule in app.url_map.iter_rules()]
        expected_routes = [
            '/health',
            '/api/recommendations'
        ]
        
        for route in expected_routes:
            if route in routes:
                print(f"✓ Route {route} registered")
            else:
                print(f"✗ Route {route} NOT registered")
                return False
        
        return True
    except Exception as e:
        print(f"✗ API endpoint test error: {e}")
        return False


def main():
    """Run all tests"""
    print("=" * 60)
    print("ML Service Structure Validation")
    print("=" * 60)
    
    tests = [
        test_imports,
        test_app_structure,
        test_recommendation_engine,
        test_api_endpoints
    ]
    
    results = []
    for test in tests:
        try:
            results.append(test())
        except Exception as e:
            print(f"✗ Test failed with exception: {e}")
            results.append(False)
    
    print("\n" + "=" * 60)
    print(f"Results: {sum(results)}/{len(results)} tests passed")
    print("=" * 60)
    
    if all(results):
        print("\n✓ All structure tests passed!")
        return 0
    else:
        print("\n✗ Some tests failed")
        return 1


if __name__ == '__main__':
    sys.exit(main())
