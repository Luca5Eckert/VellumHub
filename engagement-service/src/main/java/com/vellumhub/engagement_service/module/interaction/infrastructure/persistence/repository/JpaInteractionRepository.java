package com.vellumhub.engagement_service.module.interaction.infrastructure.persistence.repository;

import com.vellumhub.engagement_service.module.interaction.domain.model.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaInteractionRepository extends JpaRepository<Interaction, Long> {
    List<Interaction> findAllByUserId(UUID userId);
}
