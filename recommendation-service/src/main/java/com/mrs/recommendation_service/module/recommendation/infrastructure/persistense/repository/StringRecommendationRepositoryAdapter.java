package com.mrs.recommendation_service.module.recommendation.infrastructure.persistense.repository;

import com.mrs.recommendation_service.module.recommendation.domain.model.Recommendation;
import com.mrs.recommendation_service.module.recommendation.domain.port.RecommendationRepository;

import java.util.Optional;
import java.util.UUID;

public class StringRecommendationRepositoryAdapter implements RecommendationRepository {

    private final RecommendationRepositoryJpa recommendationRepositoryJpa;

    public StringRecommendationRepositoryAdapter(RecommendationRepositoryJpa recommendationRepositoryJpa) {
        this.recommendationRepositoryJpa = recommendationRepositoryJpa;
    }

    @Override
    public void save(Recommendation recommendation) {
        recommendationRepositoryJpa.save(recommendation);
    }

    @Override
    public Optional<Recommendation> findById(UUID id) {
        return recommendationRepositoryJpa.findById(id);
    }

    @Override
    public void deleteById(UUID id) {
        recommendationRepositoryJpa.deleteById(id);
    }

}
