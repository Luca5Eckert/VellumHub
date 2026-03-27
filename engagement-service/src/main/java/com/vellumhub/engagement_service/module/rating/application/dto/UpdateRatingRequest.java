package com.vellumhub.engagement_service.module.rating.application.dto;

public record UpdateRatingRequest(
        Integer stars,
        String review
) {
}
