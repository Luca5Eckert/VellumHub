package com.vellumhub.engagement_service.module.reaction.domain.event;

import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;

import java.util.UUID;

public record ReactionChangedEvent(
        UUID userId,
        UUID bookId,
        String typeReaction
) {

    public static ReactionChangedEvent from(Reaction reaction) {
        return new ReactionChangedEvent(
                reaction.getUserId(),
                reaction.getBookSnapshot().getBookId(),
                reaction.getTypeReaction().name()
        );
    }
}
