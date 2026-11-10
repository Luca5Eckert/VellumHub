package com.vellumhub.kafka.avro;

import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.kafka.avro.book.BookChangeKind;
import com.vellumhub.kafka.avro.book.BookState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Explicit, lossless wire mapping. Does not regenerate event IDs, keys or revisions. */
public final class BookAvroMapper {
    private BookAvroMapper() {}
    public static com.vellumhub.kafka.avro.book.BookChangedEvent toAvro(BookChangedEvent event) {
        BookState state = event.state() == null ? null : BookState.newBuilder()
                .setTitle(event.state().title()).setDescription(event.state().description())
                .setReleaseYear(event.state().releaseYear()).setCoverUrl(event.state().coverUrl())
                .setAuthor(event.state().author()).setIsbn(event.state().isbn())
                .setPageCount(event.state().pageCount()).setPublisher(event.state().publisher())
                .setGenres(event.state().genres()).build();
        return com.vellumhub.kafka.avro.book.BookChangedEvent.newBuilder()
                .setEventId(event.eventId().toString()).setOccurredAt(event.occurredAt().toString())
                .setBookId(event.bookId().toString()).setRevision(event.revision())
                .setKind(BookChangeKind.valueOf(event.kind().name()))
                .setState(state).setCatalogSource(null).build();
    }
    public static BookChangedEvent fromAvro(com.vellumhub.kafka.avro.book.BookChangedEvent event) {
        if (event == null) throw new IllegalArgumentException("Null Avro Book event");
        BookState snapshot = event.getState();
        BookChangedEvent.State state = snapshot == null ? null : new BookChangedEvent.State(
                snapshot.getTitle(), snapshot.getDescription(), snapshot.getReleaseYear(),
                snapshot.getCoverUrl(), snapshot.getAuthor(), snapshot.getIsbn(),
                snapshot.getPageCount(), snapshot.getPublisher(), List.copyOf(snapshot.getGenres()));
        return new BookChangedEvent(UUID.fromString(event.getEventId()),
                Instant.parse(event.getOccurredAt()), UUID.fromString(event.getBookId()),
                event.getRevision(), BookChangedEvent.Kind.valueOf(event.getKind().name()), state);
    }
    public static BookChangedEvent fromAvro(String key,
            com.vellumhub.kafka.avro.book.BookChangedEvent event) {
        BookChangedEvent result = fromAvro(event);
        if (!result.bookId().toString().equals(key))
            throw new IllegalArgumentException("Kafka key and Book ID differ");
        return result;
    }
}
