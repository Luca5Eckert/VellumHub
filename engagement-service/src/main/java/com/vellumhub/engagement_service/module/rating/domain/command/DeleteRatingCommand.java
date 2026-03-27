package com.mrs.engagement_service.module.rating.domain.command;

import java.util.UUID;

public record DeleteRatingCommand(
        Long ratingId,
        UUID userId
) {
}
