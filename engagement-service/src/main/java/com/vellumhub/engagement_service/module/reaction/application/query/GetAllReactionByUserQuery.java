package com.vellumhub.engagement_service.module.reaction.application.query;

import java.util.UUID;

public record GetAllReactionByUserQuery(
        UUID userId
) {
    public static GetAllReactionByUserQuery of(UUID userId) {
        return new GetAllReactionByUserQuery(userId);
    }
}
