"""
ML Service for Media Recommendation System
Receives user profile via API and fetches media features from recommendation_db
"""

from flask import Flask, jsonify, request
from flask_cors import CORS
import logging
import os
from dotenv import load_dotenv

from services.recommendation_engine import RecommendationEngine
from database.db_connection import DatabaseConnection
from database.media_feature_repository import MediaFeatureRepository

# Load environment variables
load_dotenv()

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Configuration constants
MAX_RECOMMENDATIONS_LIMIT = int(os.getenv('MAX_RECOMMENDATIONS_LIMIT', '100'))
MAX_MEDIA_FETCH_LIMIT = int(os.getenv('MAX_MEDIA_FETCH_LIMIT', '1000'))

# Initialize Flask app
app = Flask(__name__)
CORS(app)

# Initialize database connection (only to recommendation_db)
db_connection = DatabaseConnection(
    host=os.getenv('DB_HOST', 'localhost'),
    port=int(os.getenv('DB_PORT', '5432')),
    database=os.getenv('DB_NAME', 'recommendation_db'),
    user=os.getenv('DB_USER', 'user'),
    password=os.getenv('DB_PASSWORD', 'password')
)

# Initialize repository
media_feature_repo = MediaFeatureRepository(db_connection)

# Initialize recommendation engine
recommendation_engine = RecommendationEngine()


@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        'status': 'healthy',
        'service': 'ml-service',
        'version': '2.0.0'
    }), 200


@app.route('/api/recommendations', methods=['POST'])
def calculate_recommendations():
    """
    Calculate recommendations based on user profile
    Fetches media features from recommendation_db
    
    Request Body:
        {
            "user_profile": {
                "user_id": "uuid",
                "genre_scores": {"ACTION": 5.0, "THRILLER": 3.0},
                "interacted_media_ids": ["uuid1", "uuid2"],
                "total_engagement_score": 100.0
            },
            "limit": 10
        }
    
    Returns:
        JSON response with scored recommendations
    """
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({
                'error': 'Request body is required'
            }), 400
        
        # Validate required fields
        if 'user_profile' not in data:
            return jsonify({
                'error': 'user_profile is required'
            }), 400
        
        user_profile = data['user_profile']
        limit = data.get('limit', 10)
        
        # Validate limit
        if not isinstance(limit, int) or limit < 1 or limit > MAX_RECOMMENDATIONS_LIMIT:
            return jsonify({
                'error': f'limit must be an integer between 1 and {MAX_RECOMMENDATIONS_LIMIT}'
            }), 400
        
        # Extract interacted media IDs to exclude
        interacted_ids = set(user_profile.get('interacted_media_ids', []))
        
        # Fetch media features from database (excluding already interacted)
        logger.info(f"Fetching media features for user {user_profile.get('user_id')}, excluding {len(interacted_ids)} interacted media")
        available_media = media_feature_repo.get_all_media_features(
            exclude_media_ids=interacted_ids,
            limit=MAX_MEDIA_FETCH_LIMIT  # Configurable: fetch top N by popularity, then ML ranks
        )
        
        if not available_media:
            logger.warning("No available media found in database")
            return jsonify({
                'user_id': user_profile.get('user_id'),
                'recommendations': [],
                'count': 0
            }), 200
        
        # Calculate recommendations
        logger.info(f"Calculating recommendations for user {user_profile.get('user_id')} with {len(available_media)} media items")
        
        recommendations = recommendation_engine.calculate_recommendations(
            user_profile=user_profile,
            available_media=available_media,
            limit=limit
        )
        
        return jsonify({
            'user_id': user_profile.get('user_id'),
            'recommendations': recommendations,
            'count': len(recommendations)
        }), 200
        
    except ValueError as e:
        logger.error(f"Validation error: {str(e)}")
        return jsonify({
            'error': f'Validation error: {str(e)}'
        }), 400
    except Exception as e:
        logger.error(f"Error calculating recommendations: {str(e)}", exc_info=True)
        return jsonify({
            'error': 'Internal server error',
            'message': str(e)
        }), 500


@app.errorhandler(404)
def not_found(error):
    """Handle 404 errors"""
    return jsonify({
        'error': 'Endpoint not found'
    }), 404


@app.errorhandler(500)
def internal_error(error):
    """Handle 500 errors"""
    logger.error(f"Internal server error: {str(error)}", exc_info=True)
    return jsonify({
        'error': 'Internal server error'
    }), 500


if __name__ == '__main__':
    port = int(os.getenv('PORT', '5000'))
    debug = os.getenv('DEBUG', 'False').lower() == 'true'
    
    logger.info(f"Starting ML Service on port {port}")
    app.run(host='0.0.0.0', port=port, debug=debug)
