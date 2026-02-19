package com.mrs.engagement_service.module.rating.domain.command;

public record UpdateRatingCommand(
        long ratingId,
        int stars,
        String review
) {
}
