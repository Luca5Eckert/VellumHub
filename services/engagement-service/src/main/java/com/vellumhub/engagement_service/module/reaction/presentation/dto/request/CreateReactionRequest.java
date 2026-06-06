package com.vellumhub.engagement_service.module.reaction.presentation.dto.request;

import com.vellumhub.engagement_service.module.reaction.domain.model.TypeReaction;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateReactionRequest(
        @NotNull UUID bookId,
        @NotNull TypeReaction typeReaction
) {
}
