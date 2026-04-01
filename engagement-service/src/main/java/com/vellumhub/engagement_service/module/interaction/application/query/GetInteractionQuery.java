package com.vellumhub.engagement_service.module.interaction.application.query;

public record GetInteractionQuery(
        Long interactionId
) {
    public static GetInteractionQuery of(Long id) {
        return new GetInteractionQuery(id);
    }
}
