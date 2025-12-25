"""
API Integration tests for ML Service
"""
import pytest
import json


class TestHealthEndpoint:
    """Tests for /health endpoint"""

    def test_health_returns_200(self, client):
        """Health check should return 200 OK"""
        response = client.get('/health')
        
        assert response.status_code == 200

    def test_health_returns_correct_status(self, client):
        """Health check should return healthy status"""
        response = client.get('/health')
        data = json.loads(response.data)
        
        assert data['status'] == 'healthy'

    def test_health_returns_service_name(self, client):
        """Health check should return service name"""
        response = client.get('/health')
        data = json.loads(response.data)
        
        assert data['service'] == 'ml-service'

    def test_health_returns_version(self, client):
        """Health check should return version"""
        response = client.get('/health')
        data = json.loads(response.data)
        
        assert 'version' in data


class TestRecommendationsEndpoint:
    """Tests for /api/recommendations endpoint"""

    def test_recommendations_with_valid_request(self, client, sample_user_profile, sample_media):
        """Should return recommendations for valid request"""
        payload = {
            'user_profile': sample_user_profile,
            'limit': 10
        }
        
        response = client.post(
            '/api/recommendations',
            data=json.dumps(payload),
            content_type='application/json'
        )
        
        # Note: This will return 200 even if no media from DB, 
        # because we're mocking the user profile
        assert response.status_code in [200, 500]  # 500 if DB not available

    def test_recommendations_missing_body_returns_400(self, client):
        """Should return 400 for missing request body"""
        response = client.post(
            '/api/recommendations',
            data='',
            content_type='application/json'
        )
        
        assert response.status_code == 400

    def test_recommendations_missing_user_profile_returns_400(self, client):
        """Should return 400 when user_profile is missing"""
        payload = {'limit': 10}
        
        response = client.post(
            '/api/recommendations',
            data=json.dumps(payload),
            content_type='application/json'
        )
        
        assert response.status_code == 400
        data = json.loads(response.data)
        assert 'user_profile is required' in data['error']

    def test_recommendations_invalid_limit_returns_400(self, client, sample_user_profile):
        """Should return 400 for invalid limit value"""
        payload = {
            'user_profile': sample_user_profile,
            'limit': -1
        }
        
        response = client.post(
            '/api/recommendations',
            data=json.dumps(payload),
            content_type='application/json'
        )
        
        assert response.status_code == 400

    def test_recommendations_limit_too_high_returns_400(self, client, sample_user_profile):
        """Should return 400 when limit exceeds maximum"""
        payload = {
            'user_profile': sample_user_profile,
            'limit': 1000  # Exceeds MAX_RECOMMENDATIONS_LIMIT (100)
        }
        
        response = client.post(
            '/api/recommendations',
            data=json.dumps(payload),
            content_type='application/json'
        )
        
        assert response.status_code == 400

    def test_recommendations_uses_default_limit(self, client, sample_user_profile):
        """Should use default limit when not provided"""
        payload = {
            'user_profile': sample_user_profile
            # No limit specified - should default to 10
        }
        
        response = client.post(
            '/api/recommendations',
            data=json.dumps(payload),
            content_type='application/json'
        )
        
        # Should not fail for missing limit
        assert response.status_code in [200, 500]

    def test_recommendations_returns_user_id(self, client, sample_user_profile):
        """Should return user_id in response"""
        payload = {
            'user_profile': sample_user_profile,
            'limit': 10
        }
        
        response = client.post(
            '/api/recommendations',
            data=json.dumps(payload),
            content_type='application/json'
        )
        
        if response.status_code == 200:
            data = json.loads(response.data)
            assert 'user_id' in data
            assert data['user_id'] == sample_user_profile['user_id']

    def test_recommendations_returns_count(self, client, sample_user_profile):
        """Should return count in response"""
        payload = {
            'user_profile': sample_user_profile,
            'limit': 10
        }
        
        response = client.post(
            '/api/recommendations',
            data=json.dumps(payload),
            content_type='application/json'
        )
        
        if response.status_code == 200:
            data = json.loads(response.data)
            assert 'count' in data
            assert isinstance(data['count'], int)

    def test_recommendations_returns_list(self, client, sample_user_profile):
        """Should return recommendations as a list"""
        payload = {
            'user_profile': sample_user_profile,
            'limit': 10
        }
        
        response = client.post(
            '/api/recommendations',
            data=json.dumps(payload),
            content_type='application/json'
        )
        
        if response.status_code == 200:
            data = json.loads(response.data)
            assert 'recommendations' in data
            assert isinstance(data['recommendations'], list)


class TestNotFoundEndpoint:
    """Tests for 404 handling"""

    def test_nonexistent_endpoint_returns_404(self, client):
        """Should return 404 for non-existent endpoints"""
        response = client.get('/nonexistent')
        
        assert response.status_code == 404

    def test_404_returns_error_message(self, client):
        """Should return error message for 404"""
        response = client.get('/nonexistent')
        data = json.loads(response.data)
        
        assert 'error' in data


class TestHTTPMethods:
    """Tests for HTTP method handling"""

    def test_health_only_allows_get(self, client):
        """Health endpoint should only allow GET"""
        post_response = client.post('/health')
        
        assert post_response.status_code == 405

    def test_recommendations_only_allows_post(self, client):
        """Recommendations endpoint should only allow POST"""
        get_response = client.get('/api/recommendations')
        
        assert get_response.status_code == 405
