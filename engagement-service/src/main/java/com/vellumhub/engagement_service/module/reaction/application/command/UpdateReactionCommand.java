package com.vellumhub.engagement_service.module.reaction.application.command;

import com.vellumhub.engagement_service.module.reaction.domain.model.TypeReaction;

import java.util.UUID;

public record UpdateReactionCommand(
        UUID userId,
        Long interactionId,
        TypeReaction typeReaction
) {
    public static UpdateReactionCommand of(UUID userId, Long id, TypeReaction typeReaction) {
        return new UpdateReactionCommand(
                userId,
                id,
                typeReaction
        );
    }
}
