package com.vellumhub.kafka.avro;

import com.vellumhub.kafka.avro.book.BookChangeKind;
import com.vellumhub.kafka.avro.book.BookState;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Validates and maps Book integration events to the generated wire contract. */
public final class BookAvroMapper {
    private BookAvroMapper() {}

    public static com.vellumhub.kafka.avro.book.BookChangedEvent encode(BookChangedEvent event) {
        Objects.requireNonNull(event, "event");
        BookState state = event.state() == null ? null : BookState.newBuilder()
                .setTitle(event.state().title())
                .setDescription(event.state().description())
                .setReleaseYear(event.state().releaseYear())
                .setCoverUrl(event.state().coverUrl())
                .setAuthor(event.state().author())
                .setIsbn(event.state().isbn())
                .setPageCount(event.state().pageCount())
                .setPublisher(event.state().publisher())
                .setGenres(new ArrayList<>(event.state().genres())).build();
        return com.vellumhub.kafka.avro.book.BookChangedEvent.newBuilder()
                .setEventId(event.eventId().toString())
                .setOccurredAt(event.occurredAt().toString())
                .setBookId(event.bookId().toString())
                .setRevision(event.revision())
                .setKind(BookChangeKind.valueOf(event.kind().name()))
                .setState(state)
                .setCatalogSource(null).build();
    }

    public static BookChangedEvent decode(com.vellumhub.kafka.avro.book.BookChangedEvent wire) {
        Objects.requireNonNull(wire, "wire");
        try {
            BookState s = wire.getState();
            BookChangedEvent.State state = s == null ? null : new BookChangedEvent.State(
                    s.getTitle().toString(), s.getDescription() == null ? null : s.getDescription().toString(),
                    s.getReleaseYear(), s.getCoverUrl() == null ? null : s.getCoverUrl().toString(),
                    s.getAuthor() == null ? null : s.getAuthor().toString(),
                    s.getIsbn() == null ? null : s.getIsbn().toString(),
                    s.getPageCount(), s.getPublisher() == null ? null : s.getPublisher().toString(),
                    List.copyOf(s.getGenres().stream().map(Object::toString).toList()));
            return new BookChangedEvent(
                    UUID.fromString(wire.getEventId().toString()),
                    Instant.parse(wire.getOccurredAt().toString()),
                    UUID.fromString(wire.getBookId().toString()),
                    wire.getRevision(), BookChangedEvent.Kind.valueOf(wire.getKind().name()), state);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid Book Avro value", e);
        }
    }

    public static BookChangedEvent decodeForKey(
            com.vellumhub.kafka.avro.book.BookChangedEvent wire, String key) {
        BookChangedEvent event = decode(wire);
        if (!event.bookId().toString().equals(key))
            throw new IllegalArgumentException("Kafka Book key does not match bookId");
        return event;
    }
}
