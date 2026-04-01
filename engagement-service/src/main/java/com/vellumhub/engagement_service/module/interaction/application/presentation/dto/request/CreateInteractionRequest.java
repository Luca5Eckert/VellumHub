package com.vellumhub.engagement_service.module.interaction.application.presentation.dto.request;

import java.util.UUID;

public record CreateInteractionRequest(
        UUID bookId,
        String typeInteraction
) {
}
