package com.mrs.engagement_service.application.dto;

import java.util.UUID;

public record GetMediaStatusResponse(
        UUID mediaId,
        long totalViews,
        long totalLikes,
        long totalDislikes,
        double averageRating,
        long totalRatings,
        long totalInteractions,
        double popularityScore
) {

}
