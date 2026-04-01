package com.vellumhub.engagement_service.module.interaction.presentation.dto.request;

import com.vellumhub.engagement_service.module.interaction.domain.model.TypeInteraction;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateInteractionRequest(
        @NotNull UUID bookId,
        @NotNull TypeInteraction typeInteraction
) {
}
