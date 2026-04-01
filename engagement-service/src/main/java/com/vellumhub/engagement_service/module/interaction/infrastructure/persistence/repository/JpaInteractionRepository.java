package com.vellumhub.engagement_service.module.interaction.infrastructure.persistence.repository;

import com.vellumhub.engagement_service.module.interaction.domain.model.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaInteractionRepository extends JpaRepository<Interaction, Long> {
}
