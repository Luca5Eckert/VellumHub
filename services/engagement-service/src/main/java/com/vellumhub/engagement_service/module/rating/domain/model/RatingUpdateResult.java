package com.vellumhub.engagement_service.module.rating.domain.model;

public record RatingUpdateResult(
        Rating rating,
        int oldStars,
        boolean reviewChanged
) {
}
