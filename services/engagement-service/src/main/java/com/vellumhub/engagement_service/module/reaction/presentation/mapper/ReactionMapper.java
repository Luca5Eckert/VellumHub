package com.vellumhub.engagement_service.module.reaction.presentation.mapper;

import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.presentation.dto.response.ReactionResponse;
import org.springframework.stereotype.Component;

@Component
public class ReactionMapper {

    public ReactionResponse toResponse(
            Reaction reaction
    ) {
        return new ReactionResponse(
                reaction.getId(),
                reaction.getUserId(),
                reaction.getBookSnapshot().getBookId(),
                reaction.getTypeReaction()
        );
    }

}
