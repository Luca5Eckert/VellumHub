package com.vellumhub.engagement_service.module.reaction.domain.event;

import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.model.TypeReaction;

import java.util.UUID;

public record ReactionEvent(
        UUID userId,
        UUID bookId,
        String typeReaction
) {

    public static ReactionEvent from(Reaction reaction) {
        return new ReactionEvent(
                reaction.getUserId(),
                reaction.getBookSnapshot().getBookId(),
                reaction.getTypeReaction().name()
        );
    }
}
