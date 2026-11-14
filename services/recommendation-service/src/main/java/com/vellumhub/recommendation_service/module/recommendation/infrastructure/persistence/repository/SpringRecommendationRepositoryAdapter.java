package com.vellumhub.recommendation_service.module.recommendation.infrastructure.persistence.repository;

import com.vellumhub.recommendation_service.module.recommendation.domain.model.Recommendation;
import com.vellumhub.recommendation_service.module.recommendation.infrastructure.cache.RecommendationResponseCache;
import com.vellumhub.recommendation_service.module.recommendation.domain.port.RecommendationRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SpringRecommendationRepositoryAdapter implements RecommendationRepository {

    private final RecommendationRepositoryJpa recommendationRepositoryJpa;
    private final RecommendationResponseCache cache;

    public SpringRecommendationRepositoryAdapter(RecommendationRepositoryJpa recommendationRepositoryJpa,
            RecommendationResponseCache cache) {
        this.recommendationRepositoryJpa = recommendationRepositoryJpa;
        this.cache = cache;
    }

    @Override
    public void save(Recommendation recommendation) {
        recommendationRepositoryJpa.save(recommendation);
        cache.invalidateAllAfterCommit();
    }

    @Override
    public Optional<Recommendation> findById(UUID id) {
        return recommendationRepositoryJpa.findById(id);
    }

    @Override
    public void deleteById(UUID id) {
        recommendationRepositoryJpa.deleteById(id);
        cache.invalidateAllAfterCommit();
    }

    @Override
    public List<Recommendation> findAllById(List<UUID> booksId) {
        return booksId.isEmpty() ? List.of() : recommendationRepositoryJpa.findPageWithGenres(booksId);
    }

}
