package com.vellumhub.engagement_service.module.reaction.application.query;

import java.util.UUID;

public record GetReactionQuery(
        Long interactionId,
        UUID requesterId
) {
    public static GetReactionQuery of(Long id, UUID requesterId) {
        return new GetReactionQuery(id, requesterId);
    }
}
