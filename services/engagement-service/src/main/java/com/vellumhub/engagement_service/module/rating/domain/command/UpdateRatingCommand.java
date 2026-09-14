package com.vellumhub.engagement_service.module.rating.domain.command;

public record UpdateRatingCommand(
        long ratingId,
        Integer stars,
        String review
) {
}
