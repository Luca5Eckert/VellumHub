package com.mrs.engagement_service.infrastructure.repository;

import com.mrs.engagement_service.domain.model.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface EngagementRepositoryJpa extends JpaRepository<Interaction, Long>, JpaSpecificationExecutor<Interaction> {
}
