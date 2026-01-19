package com.mrs.recommendation_service.domain.port;

import com.mrs.recommendation_service.domain.model.MediaFeature;

import java.util.Optional;
import java.util.UUID;

public interface MediaFeatureRepository{
    void save(MediaFeature mediaFeature);

    void deleteById(UUID uuid);

    Optional<MediaFeature> findById(UUID uuid);
}
