package com.vellumhub.engagement_service.module.interaction.infrastructure.persistence.repository;

import com.vellumhub.engagement_service.module.interaction.domain.model.Interaction;
import com.vellumhub.engagement_service.module.interaction.domain.port.InteractionRepository;
import org.springframework.stereotype.Repository;

@Repository
public class SpringInteractionRepository implements InteractionRepository {

    private final JpaInteractionRepository jpaInteractionRepository;

    public SpringInteractionRepository(JpaInteractionRepository jpaInteractionRepository) {
        this.jpaInteractionRepository = jpaInteractionRepository;
    }

    @Override
    public void save(Interaction interaction) {
        jpaInteractionRepository.save(interaction);
    }
}
