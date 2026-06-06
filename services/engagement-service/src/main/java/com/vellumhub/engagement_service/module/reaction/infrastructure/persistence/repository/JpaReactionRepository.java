package com.vellumhub.engagement_service.module.reaction.infrastructure.persistence.repository;

import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaReactionRepository extends JpaRepository<Reaction, Long> {
    List<Reaction> findAllByUserId(UUID userId);
}
