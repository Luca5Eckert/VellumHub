package com.vellumhub.engagement_service.module.interaction.domain.port;

import com.vellumhub.engagement_service.module.interaction.domain.model.Interaction;

import java.util.Optional;

public interface InteractionRepository {
    void save(Interaction interaction);

    Optional<Interaction> findById(Long aLong);
}
