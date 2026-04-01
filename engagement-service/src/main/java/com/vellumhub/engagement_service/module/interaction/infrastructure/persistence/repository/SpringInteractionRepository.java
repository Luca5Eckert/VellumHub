package com.vellumhub.engagement_service.module.interaction.infrastructure.persistence.repository;

import com.vellumhub.engagement_service.module.interaction.domain.model.Interaction;
import com.vellumhub.engagement_service.module.interaction.domain.port.InteractionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

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

    @Override
    public Optional<Interaction> findById(Long id) {
        return jpaInteractionRepository.findById(id);
    }
}
