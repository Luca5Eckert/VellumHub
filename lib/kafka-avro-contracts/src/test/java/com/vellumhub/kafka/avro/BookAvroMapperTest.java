package com.vellumhub.kafka.avro;

import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookAvroMapperTest {
    @Test void roundTripsAllKindsWithoutChangingIdentityOrRevision() {
        var id = UUID.randomUUID();
        var book = UUID.randomUUID();
        for (var kind : BookChangedEvent.Kind.values()) {
            var snapshot = kind == BookChangedEvent.Kind.DELETED ? null :
                    new BookChangedEvent.State("Book", null, 2025, null, null, null, 300, null, List.of("SF"));
            var input = new BookChangedEvent(id, Instant.parse("2026-11-06T12:00:00Z"),
                    book, 19, kind, snapshot);
            var wire = BookAvroMapper.toAvro(input);
            assertEquals(id.toString(), wire.getEventId());
            assertEquals(19, wire.getRevision());
            assertEquals(input, BookAvroMapper.fromAvro(book.toString(), wire));
            assertThrows(IllegalArgumentException.class,
                    () -> BookAvroMapper.fromAvro(UUID.randomUUID().toString(), wire));
        }
    }
    @Test void invalidTombstoneCannotEnterDomainProjection() {
        var wire = com.vellumhub.kafka.avro.book.BookChangedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now().toString())
                .setBookId(UUID.randomUUID().toString()).setRevision(0)
                .setKind(com.vellumhub.kafka.avro.book.BookChangeKind.CREATED)
                .setState(null).setCatalogSource(null).build();
        assertThrows(IllegalArgumentException.class, () -> BookAvroMapper.fromAvro(wire));
    }
}
