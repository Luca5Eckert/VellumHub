package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.reaction.application.query.GetReactionQuery;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.exception.ReactionNotFoundException;
import java.util.Objects;
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
        Reaction reaction = reactionRepository.findById(query.interactionId())
                .orElseThrow(ReactionNotFoundException::new);
        if (query.requesterId() == null || !Objects.equals(reaction.getUserId(), query.requesterId())) {
            throw new ReactionNotFoundException();
        }
        return reaction;
    }

}
