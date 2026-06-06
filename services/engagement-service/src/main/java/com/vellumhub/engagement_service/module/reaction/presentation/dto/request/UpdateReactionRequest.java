package com.vellumhub.engagement_service.module.reaction.presentation.dto.request;

import com.vellumhub.engagement_service.module.reaction.domain.model.TypeReaction;

public record UpdateReactionRequest(
        TypeReaction typeReaction
) {
}
