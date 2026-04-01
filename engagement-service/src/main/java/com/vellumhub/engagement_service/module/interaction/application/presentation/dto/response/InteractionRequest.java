package com.vellumhub.engagement_service.module.interaction.application.presentation.dto.response;

import com.vellumhub.engagement_service.module.interaction.domain.model.TypeInteraction;

import java.util.UUID;

public record InteractionRequest(
        Long interactionId,
        UUID userId,
        UUID bookId,
        TypeInteraction typeInteraction
) {
}
