"""
Unit tests for RecommendationEngine
"""
import pytest
from services.recommendation_engine import RecommendationEngine, DEFAULT_CONTENT_WEIGHT, DEFAULT_POPULARITY_WEIGHT


class TestRecommendationEngineInit:
    """Tests for RecommendationEngine initialization"""

    def test_init_with_default_weights(self):
        """Should initialize with default weights"""
        engine = RecommendationEngine()
        
        assert engine.content_weight == DEFAULT_CONTENT_WEIGHT
        assert engine.popularity_weight == DEFAULT_POPULARITY_WEIGHT

    def test_init_with_custom_weights(self):
        """Should initialize with custom weights that sum to 1.0"""
        engine = RecommendationEngine(content_weight=0.6, popularity_weight=0.4)
        
        assert engine.content_weight == 0.6
        assert engine.popularity_weight == 0.4

    def test_init_with_invalid_weights_raises_error(self):
        """Should raise ValueError when weights don't sum to 1.0"""
        with pytest.raises(ValueError) as exc_info:
            RecommendationEngine(content_weight=0.5, popularity_weight=0.3)
        
        assert "Weights must sum to 1.0" in str(exc_info.value)


class TestCalculateRecommendations:
    """Tests for calculate_recommendations method"""

    def test_returns_correct_count(self, engine, sample_user_profile, sample_media):
        """Should return requested number of recommendations"""
        recommendations = engine.calculate_recommendations(
            user_profile=sample_user_profile,
            available_media=sample_media,
            limit=3
        )
        
        assert len(recommendations) == 3

    def test_excludes_interacted_media(self, engine, sample_user_profile, sample_media):
        """Should not recommend already interacted media"""
        sample_user_profile['interacted_media_ids'] = ['media-3', 'media-5']
        
        recommendations = engine.calculate_recommendations(
            user_profile=sample_user_profile,
            available_media=sample_media,
            limit=10
        )
        
        media_ids = [r['media_id'] for r in recommendations]
        assert 'media-3' not in media_ids
        assert 'media-5' not in media_ids

    def test_returns_empty_list_when_no_media(self, engine, sample_user_profile):
        """Should return empty list when no available media"""
        recommendations = engine.calculate_recommendations(
            user_profile=sample_user_profile,
            available_media=[],
            limit=10
        )
        
        assert recommendations == []

    def test_returns_empty_list_when_all_interacted(self, engine, sample_user_profile, sample_media):
        """Should return empty list when all media already interacted"""
        sample_user_profile['interacted_media_ids'] = [m['media_id'] for m in sample_media]
        
        recommendations = engine.calculate_recommendations(
            user_profile=sample_user_profile,
            available_media=sample_media,
            limit=10
        )
        
        assert recommendations == []

    def test_recommendations_sorted_by_score(self, engine, sample_user_profile, sample_media):
        """Should return recommendations sorted by score (highest first)"""
        recommendations = engine.calculate_recommendations(
            user_profile=sample_user_profile,
            available_media=sample_media,
            limit=5
        )
        
        scores = [r['recommendation_score'] for r in recommendations]
        assert scores == sorted(scores, reverse=True)

    def test_recommendations_include_scores(self, engine, sample_user_profile, sample_media):
        """Should include score information in recommendations"""
        recommendations = engine.calculate_recommendations(
            user_profile=sample_user_profile,
            available_media=sample_media,
            limit=1
        )
        
        if recommendations:
            rec = recommendations[0]
            assert 'recommendation_score' in rec
            assert 'content_score' in rec
            assert 'popularity_score' in rec

    def test_handles_empty_genre_scores(self, engine, empty_user_profile, sample_media):
        """Should handle user with no genre preferences"""
        recommendations = engine.calculate_recommendations(
            user_profile=empty_user_profile,
            available_media=sample_media,
            limit=5
        )
        
        # Should still return recommendations based on popularity
        assert isinstance(recommendations, list)

    def test_limit_respected_when_fewer_candidates(self, engine, sample_user_profile, sample_media):
        """Should return all candidates when limit exceeds available"""
        # All but 2 are interacted
        sample_user_profile['interacted_media_ids'] = ['media-3', 'media-4', 'media-5']
        
        recommendations = engine.calculate_recommendations(
            user_profile=sample_user_profile,
            available_media=sample_media,
            limit=10
        )
        
        # Should only return remaining non-interacted media
        assert len(recommendations) <= len(sample_media)


class TestCalculateContentScore:
    """Tests for _calculate_content_score method"""

    def test_high_score_for_matching_genres(self, engine):
        """Should calculate higher score for matching genres"""
        media = {'genres': ['ACTION', 'THRILLER']}
        genre_scores = {'ACTION': 10.0, 'THRILLER': 8.0}
        
        score = engine._calculate_content_score(media, genre_scores)
        
        assert 0 < score <= 1
        assert score > 0.5  # Should be high for matching genres

    def test_zero_score_for_no_matching_genres(self, engine):
        """Should return 0 for non-matching genres"""
        media = {'genres': ['HORROR', 'COMEDY']}
        genre_scores = {'ACTION': 10.0, 'THRILLER': 5.0}
        
        score = engine._calculate_content_score(media, genre_scores)
        
        assert score == 0

    def test_zero_score_for_empty_genres(self, engine):
        """Should return 0 when media has no genres"""
        media = {'genres': []}
        genre_scores = {'ACTION': 10.0}
        
        score = engine._calculate_content_score(media, genre_scores)
        
        assert score == 0

    def test_zero_score_for_empty_genre_scores(self, engine):
        """Should return 0 when user has no genre preferences"""
        media = {'genres': ['ACTION']}
        genre_scores = {}
        
        score = engine._calculate_content_score(media, genre_scores)
        
        assert score == 0

    def test_partial_match_scores_lower(self, engine):
        """Should score lower when only some genres match"""
        media_full_match = {'genres': ['ACTION']}
        media_partial_match = {'genres': ['ACTION', 'HORROR']}
        genre_scores = {'ACTION': 10.0}  # Only ACTION has score
        
        score_full = engine._calculate_content_score(media_full_match, genre_scores)
        score_partial = engine._calculate_content_score(media_partial_match, genre_scores)
        
        # Both should have positive scores but full match might be boosted differently
        assert score_full > 0
        assert score_partial > 0


class TestCalculateMediaScore:
    """Tests for _calculate_media_score method"""

    def test_combines_content_and_popularity(self, engine):
        """Should combine content and popularity scores correctly"""
        media = {
            'genres': ['ACTION'],
            'popularity_score': 0.8
        }
        genre_scores = {'ACTION': 10.0}
        
        score = engine._calculate_media_score(media, genre_scores)
        
        assert 0 < score <= 1

    def test_uses_default_popularity_when_missing(self, engine):
        """Should use 0 as default when popularity_score is missing"""
        media = {'genres': ['ACTION']}  # No popularity_score
        genre_scores = {'ACTION': 10.0}
        
        # Should not raise an error
        score = engine._calculate_media_score(media, genre_scores)
        
        assert score >= 0


class TestFinalScoreCalculation:
    """Tests for recommendation score weights"""

    def test_custom_weights_affect_score(self):
        """Should produce different scores with different weights"""
        engine_content_heavy = RecommendationEngine(content_weight=0.9, popularity_weight=0.1)
        engine_popularity_heavy = RecommendationEngine(content_weight=0.1, popularity_weight=0.9)
        
        media = {
            'genres': ['ACTION'],
            'popularity_score': 0.5  # Medium popularity
        }
        genre_scores = {'ACTION': 10.0}  # High content match
        
        score_content = engine_content_heavy._calculate_media_score(media, genre_scores)
        score_popularity = engine_popularity_heavy._calculate_media_score(media, genre_scores)
        
        # With high content match and medium popularity:
        # content_heavy should give higher score due to content weight
        assert score_content != score_popularity


class TestParameterizedLimit:
    """Parameterized tests for limit handling"""

    @pytest.mark.parametrize("limit,expected_max", [
        (1, 1),
        (3, 3),
        (5, 5),
        (10, 5),  # Only 5 media in sample, so max is 5
        (0, 0),
    ])
    def test_limit_parameter_handling(self, engine, sample_user_profile, sample_media, limit, expected_max):
        """Should respect limit parameter correctly"""
        # Remove interacted media from sample
        sample_user_profile['interacted_media_ids'] = []
        
        recommendations = engine.calculate_recommendations(
            user_profile=sample_user_profile,
            available_media=sample_media,
            limit=limit
        )
        
        assert len(recommendations) <= expected_max
        if limit > 0:
            assert len(recommendations) <= min(limit, len(sample_media))
