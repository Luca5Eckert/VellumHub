package com.vellumhub.recommendation_service.module.recommendation.presentation.dto;

public record GetRecommendationsRequest(
        int limit,
        int offset
) {
}
