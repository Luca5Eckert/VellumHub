package com.vellumhub.kafka.contracts.readingprogress;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Ordered V2 reading progress fact. Revision begins at zero for each progress aggregate. */
public record ReadingProgressChangedEvent(
    UUID eventId, Instant occurredAt, UUID bookProgressId, UUID userId, UUID bookId,
    String progress, int oldPage, int newPage, long revision, Kind kind
) {
    public enum Kind { BASELINE, CREATED, UPDATED }
    public ReadingProgressChangedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(bookProgressId);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(bookId);
        Objects.requireNonNull(progress);
        Objects.requireNonNull(kind);
        if (revision < 0) throw new IllegalArgumentException("Revision must be >= 0");
    }
}


