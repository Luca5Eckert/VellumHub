package com.vellumhub.kafka.contracts.readingprogress;

import java.time.Instant;
import java.util.UUID;

/** Catalog occurrence metadata is nullable only for legacy messages. */
public record CreateBookProgressEvent(
        UUID eventId,
        Instant occurredAt,
        UUID bookProgressId,
        UUID userId,
        UUID bookId,
        String progress,
        int initPage
) {
    /** Source compatibility for legacy callers; never invent occurrence metadata. */
    public CreateBookProgressEvent(UUID bookProgressId, UUID userId, UUID bookId, String progress, int initPage) {
        this(null, null, bookProgressId, userId, bookId, progress, initPage);
    }
}
