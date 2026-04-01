package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.reaction.application.query.GetReactionQuery;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.port.ReactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetReactionUseCase {

    private final ReactionRepository reactionRepository;

    public GetReactionUseCase(ReactionRepository reactionRepository) {
        this.reactionRepository = reactionRepository;
    }

    @Transactional(readOnly = true)
    public Reaction execute(GetReactionQuery query) {
        return reactionRepository.findById(query.interactionId())
                .orElseThrow(() -> new RuntimeException("Reaction not found"));
    }

}
