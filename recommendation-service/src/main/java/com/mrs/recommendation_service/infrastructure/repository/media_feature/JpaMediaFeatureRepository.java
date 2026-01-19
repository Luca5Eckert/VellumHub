package com.mrs.recommendation_service.infrastructure.repository.media_feature;

import com.mrs.recommendation_service.domain.model.MediaFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JpaMediaFeatureRepository extends JpaRepository<MediaFeature, UUID> {
}
