package com.mrs.recommendation_service.module.book_feature.domain.port;

import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookFeatureRepository {
    void save(BookFeature bookFeature);

    void deleteById(UUID uuid);

    List<UUID> findAllByUserId(UUID userId, int limit, int offset);

    Optional<BookFeature> findById(UUID uuid);

    List<UUID> findMostPopularMedias(int limit, int offset);

}
