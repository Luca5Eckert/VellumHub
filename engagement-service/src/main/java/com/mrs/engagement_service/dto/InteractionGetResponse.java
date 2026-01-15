package com.mrs.engagement_service.dto;

import com.mrs.engagement_service.model.InteractionType;

import java.time.LocalDateTime;
import java.util.UUID;

public record InteractionGetResponse(
        Long id,
        UUID userId,
        UUID mediaId,
        InteractionType type,
        double interactionValue,
        LocalDateTime timestamp
) {
}
