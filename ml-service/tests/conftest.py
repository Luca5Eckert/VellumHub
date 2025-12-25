"""
Pytest configuration and fixtures for ML Service tests
"""
import pytest
import sys
import os

# Add parent directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app import app as flask_app
from services.recommendation_engine import RecommendationEngine


@pytest.fixture
def app():
    """Create and configure a test Flask application instance"""
    flask_app.config['TESTING'] = True
    flask_app.config['DEBUG'] = False
    yield flask_app


@pytest.fixture
def client(app):
    """Create a test client for the Flask application"""
    return app.test_client()


@pytest.fixture
def engine():
    """Create a RecommendationEngine instance"""
    return RecommendationEngine()


@pytest.fixture
def sample_user_profile():
    """Create a sample user profile for testing"""
    return {
        'user_id': 'test-uuid-123',
        'genre_scores': {
            'ACTION': 8.0,
            'THRILLER': 6.0,
            'HORROR': 3.0,
            'COMEDY': 1.0
        },
        'interacted_media_ids': ['media-1', 'media-2'],
        'total_engagement_score': 150.0
    }


@pytest.fixture
def sample_media():
    """Create a sample list of media items for testing"""
    return [
        {
            'media_id': 'media-3',
            'genres': ['ACTION', 'THRILLER'],
            'popularity_score': 0.9,
            'title': 'Inception',
            'description': 'A mind-bending thriller',
            'release_year': 2010,
            'media_type': 'MOVIE',
            'cover_url': 'https://example.com/inception.jpg'
        },
        {
            'media_id': 'media-4',
            'genres': ['HORROR'],
            'popularity_score': 0.6,
            'title': 'The Ring',
            'description': 'A scary horror movie',
            'release_year': 2002,
            'media_type': 'MOVIE',
            'cover_url': 'https://example.com/ring.jpg'
        },
        {
            'media_id': 'media-5',
            'genres': ['ACTION'],
            'popularity_score': 0.95,
            'title': 'Mad Max: Fury Road',
            'description': 'Non-stop action',
            'release_year': 2015,
            'media_type': 'MOVIE',
            'cover_url': 'https://example.com/madmax.jpg'
        },
        {
            'media_id': 'media-6',
            'genres': ['COMEDY'],
            'popularity_score': 0.75,
            'title': 'The Hangover',
            'description': 'A comedy classic',
            'release_year': 2009,
            'media_type': 'MOVIE',
            'cover_url': 'https://example.com/hangover.jpg'
        },
        {
            'media_id': 'media-7',
            'genres': ['ACTION', 'HORROR'],
            'popularity_score': 0.5,
            'title': 'Resident Evil',
            'description': 'Action horror film',
            'release_year': 2002,
            'media_type': 'MOVIE',
            'cover_url': 'https://example.com/residentevil.jpg'
        }
    ]


@pytest.fixture
def empty_user_profile():
    """Create a user profile with no preferences"""
    return {
        'user_id': 'new-user-uuid',
        'genre_scores': {},
        'interacted_media_ids': [],
        'total_engagement_score': 0.0
    }
