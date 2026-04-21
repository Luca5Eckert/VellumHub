package com.vellumhub.engagement_service.module.reading_session_entry.infrastructure.kafka.event;

import java.util.UUID;

public record CreateBookProgressEvent(
        UUID bookProgressId,
        UUID userId,
        UUID bookId,
        String progress,
        int initPage
) {
}
