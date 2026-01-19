package com.mrs.engagement_service.domain.event;

import com.mrs.engagement_service.domain.model.InteractionType;

import java.time.LocalDateTime;
import java.util.UUID;

public record InteractionEvent(
        long id,
        UUID userId,
        UUID mediaId,
        InteractionType interactionType,
        double interactionValue,
        LocalDateTime timestamp
) {
}
