package com.vellumhub.engagement_service.module.interaction.domain.port;

import com.vellumhub.engagement_service.module.interaction.domain.model.Interaction;

public interface InteractionRepository {
    void save(Interaction interaction);
}
