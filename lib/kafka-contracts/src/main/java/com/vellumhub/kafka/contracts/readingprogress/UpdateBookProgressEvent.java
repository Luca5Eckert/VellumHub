package com.vellumhub.kafka.contracts.readingprogress;

import java.time.Instant;
import java.util.UUID;

/** Catalog occurrence metadata is nullable only for legacy messages. */
public record UpdateBookProgressEvent(
        UUID eventId,
        Instant occurredAt,
        UUID bookProgressId,
        UUID userId,
        UUID bookId,
        String progress,
        int oldPage, int newPage
) {
    /** Source compatibility for legacy callers; never invent occurrence metadata. */
    public UpdateBookProgressEvent(UUID bookProgressId, UUID userId, UUID bookId, String progress, int oldPage, int newPage) {
        this(null, null, bookProgressId, userId, bookId, progress, oldPage, newPage);
    }
}
