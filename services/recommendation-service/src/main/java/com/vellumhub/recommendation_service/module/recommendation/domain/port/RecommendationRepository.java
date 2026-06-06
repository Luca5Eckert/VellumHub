package com.vellumhub.recommendation_service.module.recommendation.domain.port;

import com.vellumhub.recommendation_service.module.recommendation.domain.model.Recommendation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationRepository {
    void save(Recommendation recommendation);

    Optional<Recommendation> findById(UUID id);

    void deleteById(UUID id);

    List<Recommendation> findAllById(List<UUID> booksId);
}
