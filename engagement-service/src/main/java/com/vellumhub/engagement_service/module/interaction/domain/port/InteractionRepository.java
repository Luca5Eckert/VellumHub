package com.vellumhub.engagement_service.module.interaction.domain.port;

import com.vellumhub.engagement_service.module.interaction.domain.model.Interaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InteractionRepository {
    void save(Interaction interaction);

    Optional<Interaction> findById(Long id);

    List<Interaction> findAllByUserId(UUID userId);
}
