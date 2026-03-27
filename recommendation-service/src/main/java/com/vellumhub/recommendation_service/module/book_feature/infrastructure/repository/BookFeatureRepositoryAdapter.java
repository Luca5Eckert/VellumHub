package com.mrs.recommendation_service.module.book_feature.infrastructure.repository;

import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookFeatureRepositoryAdapter implements BookFeatureRepository {

    private final JpaBookFeatureRepository mediaFeatureRepositoryJpa;

    public BookFeatureRepositoryAdapter(JpaBookFeatureRepository mediaFeatureRepositoryJpa) {
        this.mediaFeatureRepositoryJpa = mediaFeatureRepositoryJpa;
    }

    @Override
    public void save(BookFeature bookFeature) {
        mediaFeatureRepositoryJpa.save(bookFeature);
    }

    @Override
    public void deleteById(UUID uuid) {
        mediaFeatureRepositoryJpa.deleteById(uuid);
    }

    @Override
    public List<UUID> findAllByUserId(UUID userId, int limit, int offset) {
        return mediaFeatureRepositoryJpa.findTopVectorRecommendations(userId, limit, offset);
    }

    @Override
    public Optional<BookFeature> findById(UUID uuid) {
        return mediaFeatureRepositoryJpa.findById(uuid);
    }

    @Override
    public List<UUID> findMostPopularMedias(int limit, int offset) {
        return mediaFeatureRepositoryJpa.findMostPopularMedias(limit, offset);
    }

}
