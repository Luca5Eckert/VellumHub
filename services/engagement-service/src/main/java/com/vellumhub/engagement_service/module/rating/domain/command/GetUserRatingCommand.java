package com.vellumhub.engagement_service.module.rating.domain.command;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GetUserRatingCommand(
        UUID userId,
        UUID requesterId,
        Integer minStars,
        Integer maxStars,
        OffsetDateTime from,
        OffsetDateTime to,
        int pageNumber,
        int pageSize
) {
}
