package com.mrs.recommendation_service.module.recommendation.infrastructure.persistence.repository;

import com.mrs.recommendation_service.module.recommendation.domain.model.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RecommendationRepositoryJpa extends JpaRepository<Recommendation, UUID> {

}
