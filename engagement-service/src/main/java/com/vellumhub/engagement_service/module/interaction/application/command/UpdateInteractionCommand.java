package com.vellumhub.engagement_service.module.interaction.application.command;

import com.vellumhub.engagement_service.module.interaction.domain.model.TypeInteraction;

import java.util.UUID;

public record UpdateInteractionCommand(
        UUID userId,
        Long interactionId,
        TypeInteraction typeInteraction
) {
}
