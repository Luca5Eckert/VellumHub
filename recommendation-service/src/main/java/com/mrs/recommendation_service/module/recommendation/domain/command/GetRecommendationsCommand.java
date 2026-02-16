package com.mrs.recommendation_service.module.recommendation.domain.command;

import java.util.UUID;

public record GetRecommendationsCommand(
        UUID userId,
        int limit,
        int offset
) {
}
