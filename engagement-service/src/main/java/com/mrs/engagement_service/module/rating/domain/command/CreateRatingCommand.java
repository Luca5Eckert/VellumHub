package com.mrs.engagement_service.module.rating.domain.command;

import java.util.UUID;

public record CreateRatingCommand(
        UUID userId,
        UUID bookId,
        Integer stars,
        String review
) {
}
