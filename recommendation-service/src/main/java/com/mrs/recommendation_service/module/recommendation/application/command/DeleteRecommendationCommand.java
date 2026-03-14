package com.mrs.recommendation_service.module.recommendation.application.command;

import java.util.UUID;

public record DeleteRecommendationCommand(
        UUID bookId
) {
}
