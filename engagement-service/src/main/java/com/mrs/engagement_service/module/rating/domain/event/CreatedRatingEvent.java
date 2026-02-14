package com.mrs.engagement_service.module.rating.domain.event;

import java.util.UUID;

public record CreatedRatingEvent(
        UUID userId,
        UUID mediaId,
        int stars
) {
}
