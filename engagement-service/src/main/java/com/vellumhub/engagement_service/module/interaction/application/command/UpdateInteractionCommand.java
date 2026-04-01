package com.vellumhub.engagement_service.module.interaction.application.command;

import com.vellumhub.engagement_service.module.interaction.domain.model.TypeInteraction;

public record UpdateInteractionCommand(
        Long interactionId,
        TypeInteraction typeInteraction
) {
}
