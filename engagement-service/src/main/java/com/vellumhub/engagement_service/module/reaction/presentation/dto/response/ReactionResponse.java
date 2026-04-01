package com.vellumhub.engagement_service.module.reaction.presentation.dto.response;

import com.vellumhub.engagement_service.module.reaction.domain.model.TypeReaction;

import java.util.UUID;

public record ReactionResponse(
        Long interactionId,
        UUID userId,
        UUID bookId,
        TypeReaction typeReaction
) {
}
