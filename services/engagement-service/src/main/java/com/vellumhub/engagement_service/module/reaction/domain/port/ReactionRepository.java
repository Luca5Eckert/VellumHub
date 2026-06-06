package com.vellumhub.engagement_service.module.reaction.domain.port;

import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReactionRepository {
    void save(Reaction reaction);

    Optional<Reaction> findById(Long id);

    List<Reaction> findAllByUserId(UUID userId);
}
