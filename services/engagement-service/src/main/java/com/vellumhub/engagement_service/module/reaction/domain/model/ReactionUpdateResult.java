package com.vellumhub.engagement_service.module.reaction.domain.model;

public record ReactionUpdateResult(
        Reaction reaction,
        TypeReaction oldTypeReaction
) {
}
