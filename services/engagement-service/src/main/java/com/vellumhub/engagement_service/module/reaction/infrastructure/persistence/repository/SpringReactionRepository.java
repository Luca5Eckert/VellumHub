package com.vellumhub.engagement_service.module.reaction.infrastructure.persistence.repository;

import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.port.ReactionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SpringReactionRepository implements ReactionRepository {

    private final JpaReactionRepository jpaReactionRepository;

    public SpringReactionRepository(JpaReactionRepository jpaReactionRepository) {
        this.jpaReactionRepository = jpaReactionRepository;
    }

    @Override
    public Reaction save(Reaction reaction) {
        return jpaReactionRepository.save(reaction);
    }

    @Override
    public Optional<Reaction> findById(Long id) {
        return jpaReactionRepository.findById(id);
    }

    @Override
    public Optional<Reaction> findByIdForUpdate(Long id) {
        return jpaReactionRepository.findByIdForUpdate(id);
    }

    @Override
    public List<Reaction> findAllByUserId(UUID userId) {
        return jpaReactionRepository.findAllByUserId(userId);
    }
}
