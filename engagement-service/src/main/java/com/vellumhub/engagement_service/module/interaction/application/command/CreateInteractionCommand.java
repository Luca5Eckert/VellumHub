package com.vellumhub.engagement_service.module.interaction.application.command;

import com.vellumhub.engagement_service.module.interaction.domain.model.TypeInteraction;

import java.util.UUID;

public record CreateInteractionCommand(
        UUID userId,
        UUID bookId,
        TypeInteraction typeInteraction
) {
    public static CreateInteractionCommand of(
            UUID userId,
            UUID bookId,
            TypeInteraction typeInteraction
    ) {
        return new CreateInteractionCommand(userId, bookId, typeInteraction);
    }
}
