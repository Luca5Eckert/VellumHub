package com.mrs.engagement_service.module.rating.application.dto;

import java.util.UUID;

public record GetBookStatusResponse(
        UUID bookId,
        double averageRating,
        long totalRatings
) {

}
