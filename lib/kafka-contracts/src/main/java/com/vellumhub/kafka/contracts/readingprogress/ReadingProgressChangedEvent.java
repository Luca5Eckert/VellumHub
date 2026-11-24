package com.vellumhub.kafka.contracts.readingprogress;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Ordered V2 reading progress fact. Revision begins at zero for each progress aggregate. */
public record ReadingProgressChangedEvent(
    UUID eventId, Instant occurredAt, UUID bookProgressId, UUID userId, UUID bookId,
    String progress, int oldPage, int newPage, long revision, Kind kind, @JsonInclude(JsonInclude.Include.NON_NULL) Instant readAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate targetEndDate
) {
    public ReadingProgressChangedEvent(UUID eventId, Instant occurredAt, UUID bookProgressId, UUID userId,
            UUID bookId, String progress, int oldPage, int newPage, long revision, Kind kind) {
        this(eventId, occurredAt, bookProgressId, userId, bookId, progress, oldPage, newPage,
                revision, kind, null, null);
    }

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


