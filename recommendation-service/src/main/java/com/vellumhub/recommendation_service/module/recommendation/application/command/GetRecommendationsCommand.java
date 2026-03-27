package com.vellumhub.recommendation_service.module.recommendation.application.command;

import java.util.UUID;

public record GetRecommendationsCommand(
        UUID userId,
        int limit,
        int offset
) {
}
