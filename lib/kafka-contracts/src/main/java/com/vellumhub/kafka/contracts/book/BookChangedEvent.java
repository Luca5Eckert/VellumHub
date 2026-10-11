package com.vellumhub.kafka.contracts.book;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Event-carried state transfer for the canonical Catalog Book aggregate.
 * A DELETED event is a tombstone: it has no state. Other kinds carry a complete public snapshot.
 * Revisions increase monotonically per bookId; a BASELINE can start from an existing revision.
 *
 * @param eventId immutable delivery identity, retained across retries
 * @param occurredAt instant at which the change was persisted in the Catalog transaction
 * @param bookId aggregate identifier and Kafka partition key
 * @param revision JPA optimistic revision of the persisted Book
 * @param kind lifecycle transition
 * @param state public representation required to rebuild downstream projections
 */
public record BookChangedEvent(UUID eventId, Instant occurredAt, UUID bookId,
                               long revision, Kind kind, State state) {
    public enum Kind { BASELINE, CREATED, UPDATED, DELETED }

    public record State(String title, String description, int releaseYear, String coverUrl,
                        String author, String isbn, int pageCount, String publisher,
                        List<String> genres) {
        public State {
            genres = List.copyOf(Objects.requireNonNull(genres, "genres"));
        }
    }

    public BookChangedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(bookId, "bookId");
        Objects.requireNonNull(kind, "kind");
        if (revision < 0) throw new IllegalArgumentException("revision must be nonnegative");
        if (kind == Kind.DELETED && state != null)
            throw new IllegalArgumentException("Deletion must have a null snapshot");
        if (kind != Kind.DELETED && state == null)
            throw new IllegalArgumentException("Active book event requires a snapshot");
    }
}

