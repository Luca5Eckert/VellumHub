package com.mrs.recommendation_service.repository;

import com.mrs.recommendation_service.model.MediaFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MediaFeatureRepository extends JpaRepository<MediaFeature, UUID> {
}
