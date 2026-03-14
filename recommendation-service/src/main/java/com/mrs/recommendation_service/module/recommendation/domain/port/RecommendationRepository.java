package com.mrs.recommendation_service.module.recommendation.domain.port;

import com.mrs.recommendation_service.module.recommendation.domain.model.Recommendation;

public interface RecommendationRepository {
    void save(Recommendation recommendation);
}
