package com.vellumhub.engagement_service.module.interaction.application.query;

import java.util.UUID;

public record GetAllInteractionByUserQuery(
        UUID userId
) {
    public static GetAllInteractionByUserQuery of(UUID userId) {
        return new GetAllInteractionByUserQuery(userId);
    }
}
