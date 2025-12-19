"""
Stateless Recommendation Engine
Calculates recommendations based on user profile and media features without database access
"""

import logging
from typing import List, Dict, Any

logger = logging.getLogger(__name__)

# Configuration constants
DEFAULT_CONTENT_WEIGHT = 0.7
DEFAULT_POPULARITY_WEIGHT = 0.3
MAX_GENRE_SCORE = 10.0  # Expected range: 0-10 for genre scores


class RecommendationEngine:
    """
    Stateless recommendation engine that calculates recommendations
    based on user profile data and media features passed via API
    
    Algorithm:
    1. Content-Based Filtering: Matches media genres to user's genre preferences
    2. Popularity Boost: Considers media popularity scores
    3. Diversity: Ensures variety in recommendations
    """
    
    def __init__(self, content_weight=DEFAULT_CONTENT_WEIGHT, popularity_weight=DEFAULT_POPULARITY_WEIGHT):
        """
        Initialize recommendation engine with algorithm weights
        
        Args:
            content_weight: Weight for content-based scoring (0-1)
            popularity_weight: Weight for popularity scoring (0-1)
        
        Raises:
            ValueError: If weights don't sum to 1.0
        """
        # Validate weights sum to 1.0
        total_weight = content_weight + popularity_weight
        if abs(total_weight - 1.0) > 0.001:  # Allow small floating point errors
            raise ValueError(f"Weights must sum to 1.0, got {total_weight}")
        
        self.content_weight = content_weight
        self.popularity_weight = popularity_weight
        logger.info(f"RecommendationEngine initialized (content: {content_weight}, popularity: {popularity_weight})")
    
    def calculate_recommendations(
        self,
        user_profile: Dict[str, Any],
        available_media: List[Dict[str, Any]],
        limit: int = 10
    ) -> List[Dict[str, Any]]:
        """
        Calculate personalized recommendations
        
        Args:
            user_profile: User profile data containing:
                - user_id: User UUID
                - genre_scores: Dict of genre -> score
                - interacted_media_ids: List of media UUIDs user has interacted with
                - total_engagement_score: Total user engagement score
            available_media: List of media items with:
                - media_id: Media UUID
                - genres: List of genre strings
                - popularity_score: Popularity score (0-1)
                - title, description, release_year, media_type, cover_url
            limit: Maximum number of recommendations to return
        
        Returns:
            List of recommended media with scores
        """
        try:
            # Extract user preferences
            genre_scores = user_profile.get('genre_scores', {})
            interacted_ids = set(user_profile.get('interacted_media_ids', []))
            
            # Validate we have data to work with
            if not available_media:
                logger.warning("No available media to recommend")
                return []
            
            # Filter out already interacted media
            candidate_media = [
                media for media in available_media
                if media.get('media_id') not in interacted_ids
            ]
            
            if not candidate_media:
                logger.info("All available media already interacted with")
                return []
            
            # Calculate scores for each media
            scored_media = []
            for media in candidate_media:
                score = self._calculate_media_score(media, genre_scores)
                if score > 0:  # Only include media with positive scores
                    scored_media.append({
                        **media,
                        'recommendation_score': round(score, 4),
                        'content_score': round(self._calculate_content_score(media, genre_scores), 4),
                        'popularity_score': media.get('popularity_score', 0.0)
                    })
            
            # Sort by score and apply limit
            scored_media.sort(key=lambda x: x['recommendation_score'], reverse=True)
            recommendations = scored_media[:limit]
            
            logger.info(f"Generated {len(recommendations)} recommendations from {len(candidate_media)} candidates")
            return recommendations
            
        except Exception as e:
            logger.error(f"Error calculating recommendations: {str(e)}", exc_info=True)
            raise
    
    def _calculate_media_score(self, media: Dict[str, Any], genre_scores: Dict[str, float]) -> float:
        """
        Calculate overall recommendation score for a media item
        
        Args:
            media: Media item dictionary
            genre_scores: User's genre preference scores
        
        Returns:
            Combined recommendation score
        """
        content_score = self._calculate_content_score(media, genre_scores)
        popularity_score = media.get('popularity_score', 0.0)
        
        # Weighted combination
        final_score = (
            content_score * self.content_weight +
            popularity_score * self.popularity_weight
        )
        
        return final_score
    
    def _calculate_content_score(self, media: Dict[str, Any], genre_scores: Dict[str, float]) -> float:
        """
        Calculate content-based score by matching media genres to user preferences
        
        Args:
            media: Media item dictionary
            genre_scores: User's genre preference scores
        
        Returns:
            Content-based score (0-1)
        """
        media_genres = media.get('genres', [])
        
        if not media_genres or not genre_scores:
            return 0.0
        
        # Calculate average score for matching genres
        matching_scores = []
        for genre in media_genres:
            if genre in genre_scores:
                matching_scores.append(genre_scores[genre])
        
        if not matching_scores:
            return 0.0
        
        # Average of matching genre scores
        avg_score = sum(matching_scores) / len(matching_scores)
        
        # Normalize to 0-1 range (genre scores are expected to be 0-10)
        normalized_score = min(avg_score / MAX_GENRE_SCORE, 1.0)
        
        # Boost for multiple genre matches
        match_ratio = len(matching_scores) / len(media_genres)
        boosted_score = normalized_score * (0.8 + 0.2 * match_ratio)
        
        return min(boosted_score, 1.0)
