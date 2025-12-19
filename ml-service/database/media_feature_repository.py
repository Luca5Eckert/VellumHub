"""
Repository for accessing MediaFeatures from recommendation_db
"""

import logging
from typing import List, Dict, Any, Set

logger = logging.getLogger(__name__)


class MediaFeatureRepository:
    """
    Repository for fetching media features from recommendation_db
    """
    
    def __init__(self, db_connection):
        """
        Initialize repository
        
        Args:
            db_connection: DatabaseConnection instance for recommendation_db
        """
        self.db = db_connection
    
    def get_all_media_features(self, exclude_media_ids: Set[str] = None, limit: int = 1000) -> List[Dict[str, Any]]:
        """
        Fetch all media features from recommendation_db
        Excludes media that user has already interacted with
        
        Args:
            exclude_media_ids: Set of media UUIDs to exclude
            limit: Maximum number of media to fetch
        
        Returns:
            List of media feature dictionaries
        """
        try:
            params = []
            
            # Build query parts safely
            base_query = """
                SELECT 
                    media_id,
                    genres,
                    popularity_score
                FROM medias_features
            """
            
            where_clause = ""
            if exclude_media_ids and len(exclude_media_ids) > 0:
                # Safe: placeholders count is derived from data length, not user input
                placeholders = ','.join(['%s'] * len(exclude_media_ids))
                where_clause = " WHERE media_id NOT IN (" + placeholders + ")"
                params.extend(list(exclude_media_ids))
            
            order_limit = " ORDER BY popularity_score DESC LIMIT %s"
            params.append(limit)
            
            # Concatenate query parts
            query = base_query + where_clause + order_limit
            
            with self.db.get_cursor() as cursor:
                cursor.execute(query, params)
                results = cursor.fetchall()
            
            media_features = []
            for row in results:
                media_features.append({
                    'media_id': str(row['media_id']),
                    'genres': row['genres'] or [],
                    'popularity_score': float(row['popularity_score']) if row['popularity_score'] else 0.0
                })
            
            logger.info(f"Fetched {len(media_features)} media features from database")
            return media_features
            
        except Exception as e:
            logger.error(f"Error fetching media features: {str(e)}")
            raise
    
    def get_media_features_by_ids(self, media_ids: List[str]) -> List[Dict[str, Any]]:
        """
        Fetch specific media features by IDs
        
        Args:
            media_ids: List of media UUIDs
        
        Returns:
            List of media feature dictionaries
        """
        try:
            if not media_ids:
                return []
            
            # Safe: placeholders count is derived from data length, not user input
            placeholders = ','.join(['%s'] * len(media_ids))
            query = """
                SELECT 
                    media_id,
                    genres,
                    popularity_score
                FROM medias_features
                WHERE media_id IN (""" + placeholders + """)
            """
            
            with self.db.get_cursor() as cursor:
                cursor.execute(query, media_ids)
                results = cursor.fetchall()
            
            media_features = []
            for row in results:
                media_features.append({
                    'media_id': str(row['media_id']),
                    'genres': row['genres'] or [],
                    'popularity_score': float(row['popularity_score']) if row['popularity_score'] else 0.0
                })
            
            return media_features
            
        except Exception as e:
            logger.error(f"Error fetching media features by IDs: {str(e)}")
            raise
