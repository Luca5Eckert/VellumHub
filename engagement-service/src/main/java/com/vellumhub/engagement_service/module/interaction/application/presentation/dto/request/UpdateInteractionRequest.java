package com.vellumhub.engagement_service.module.interaction.application.presentation.dto.request;

import com.vellumhub.engagement_service.module.interaction.domain.model.TypeInteraction;

public record UpdateInteractionRequest(
        TypeInteraction typeInteraction
) {
}
