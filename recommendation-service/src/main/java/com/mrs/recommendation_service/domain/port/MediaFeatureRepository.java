package com.mrs.recommendation_service.domain.port;

import com.mrs.recommendation_service.application.dto.MediaFeatureResponse;
import com.mrs.recommendation_service.domain.model.MediaFeature;
import com.mrs.recommendation_service.domain.model.Recommendation;
import com.mrs.recommendation_service.domain.model.UserProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaFeatureRepository{
    void save(MediaFeature mediaFeature);

    void deleteById(UUID uuid);

    List<UUID> findAllByUserId(UUID userId, int limit, int offset);

    Optional<MediaFeature> findById(UUID uuid);

    List<UUID> findMostPopularMedias(int limit, int offset);

}
