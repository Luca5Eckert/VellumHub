package com.mrs.recommendation_service.module.recommendation.domain.port;

import com.mrs.recommendation_service.module.recommendation.domain.model.Recommendation;

import java.util.Optional;
import java.util.UUID;

public interface RecommendationRepository {
    void save(Recommendation recommendation);

    Optional<Recommendation> findById(UUID uuid);
}
