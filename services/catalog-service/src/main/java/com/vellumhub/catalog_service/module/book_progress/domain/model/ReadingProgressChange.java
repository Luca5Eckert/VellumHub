package com.vellumhub.catalog_service.module.book_progress.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence-independent result of a committed reading-progress operation.
 * This domain change does not depend on transport contracts or Kafka.
 */
public record ReadingProgressChange(
        UUID eventId, Instant occurredAt, UUID bookProgressId, UUID userId,
        UUID bookId, String progress, int oldPage, int newPage, long revision,
        Kind kind
) {
    public enum Kind { CREATED, UPDATED }
}

