package com.vellumhub.engagement_service.module.reaction.application.query;

import java.util.UUID;

public record GetAllReactionByUserQuery(
        UUID userId,
        UUID requesterId
) {
    public static GetAllReactionByUserQuery of(UUID userId, UUID requesterId) {
        return new GetAllReactionByUserQuery(userId, requesterId);
    }
}
