package com.mrs.recommendation_service.infrastructure.repository.media_feature;

import com.mrs.recommendation_service.domain.model.MediaFeature;
import com.mrs.recommendation_service.domain.port.MediaFeatureRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class MediaFeatureRepositoryAdapter implements MediaFeatureRepository {

    private final JpaMediaFeatureRepository mediaFeatureRepositoryJpa;

    public MediaFeatureRepositoryAdapter(JpaMediaFeatureRepository mediaFeatureRepositoryJpa) {
        this.mediaFeatureRepositoryJpa = mediaFeatureRepositoryJpa;
    }

    @Override
    public void save(MediaFeature mediaFeature) {
        mediaFeatureRepositoryJpa.save(mediaFeature);
    }

    @Override
    public void deleteById(UUID uuid) {
        mediaFeatureRepositoryJpa.deleteById(uuid);
    }

    @Override
    public Optional<MediaFeature> findById(UUID uuid) {
        return mediaFeatureRepositoryJpa.findById(uuid);
    }

}
