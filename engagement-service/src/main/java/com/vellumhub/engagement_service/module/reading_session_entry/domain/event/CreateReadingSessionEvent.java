package com.vellumhub.engagement_service.module.reading_session_entry.domain.event;

import com.vellumhub.engagement_service.module.reading_session_entry.domain.model.ReadingSessionEntry;

import java.time.Instant;
import java.util.UUID;

public record CreateReadingSessionEvent(
        UUID userId,
        UUID bookId,
        String type,
        Instant timestamp
) {
    public static CreateReadingSessionEvent of(ReadingSessionEntry readingSessionEntry) {
        return new CreateReadingSessionEvent(
                readingSessionEntry.getUserId(),
                readingSessionEntry.getBookSnapshot().getBookId(),
                readingSessionEntry.getReadingSessionType().name(),
                readingSessionEntry.getTimestamp()
        );
    }
}
