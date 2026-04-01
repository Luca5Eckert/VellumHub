package com.vellumhub.engagement_service.module.reaction.application.query;

public record GetReactionQuery(
        Long interactionId
) {
    public static GetReactionQuery of(Long id) {
        return new GetReactionQuery(id);
    }
}
