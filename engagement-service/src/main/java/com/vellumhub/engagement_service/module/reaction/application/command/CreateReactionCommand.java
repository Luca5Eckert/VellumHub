package com.vellumhub.engagement_service.module.reaction.application.command;

import com.vellumhub.engagement_service.module.reaction.domain.model.TypeReaction;

import java.util.UUID;

public record CreateReactionCommand(
        UUID userId,
        UUID bookId,
        TypeReaction typeReaction
) {
    public static CreateReactionCommand of(
            UUID userId,
            UUID bookId,
            TypeReaction typeReaction
    ) {
        return new CreateReactionCommand(userId, bookId, typeReaction);
    }
}
