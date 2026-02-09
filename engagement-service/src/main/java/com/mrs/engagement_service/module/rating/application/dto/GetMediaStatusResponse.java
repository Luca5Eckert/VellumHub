package com.mrs.engagement_service.module.rating.application.dto;

import java.util.UUID;

public record GetMediaStatusResponse(
        UUID mediaId,
        double averageRating,
        long totalRatings
) {

}
