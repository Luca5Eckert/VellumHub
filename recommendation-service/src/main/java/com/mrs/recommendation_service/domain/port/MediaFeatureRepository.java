package com.mrs.recommendation_service.domain.port;

import com.mrs.recommendation_service.domain.model.MediaFeature;
import com.mrs.recommendation_service.domain.model.Recommendation;
import com.mrs.recommendation_service.domain.model.UserProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaFeatureRepository{
    void save(MediaFeature mediaFeature);

    void deleteById(UUID uuid);

    List<MediaFeature> findAllByUserProfile(UserProfile userProfile);

    Optional<MediaFeature> findById(UUID uuid);
}
