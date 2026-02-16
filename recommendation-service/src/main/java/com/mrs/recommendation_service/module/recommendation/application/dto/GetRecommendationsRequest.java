package com.mrs.recommendation_service.module.recommendation.application.dto;

public record GetRecommendationsRequest(
        int limit,
        int offset
) {
}
