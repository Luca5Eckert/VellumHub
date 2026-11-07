package com.vellumhub.kafka.avro;

import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookAvroMapperTest {
    @Test void allEventKindsRoundTrip() {
        for (var kind : BookChangedEvent.Kind.values()) {
            var event = new BookChangedEvent(UUID.randomUUID(), Instant.parse("2026-11-07T12:00:00Z"),
                    UUID.randomUUID(), 9L, kind, kind == BookChangedEvent.Kind.DELETED ? null :
                    new BookChangedEvent.State("Dune", null, 1965, null, null, null, 400, null, List.of("SF")));
            var wire = BookAvroMapper.encode(event);
            assertEquals(event, BookAvroMapper.decodeForKey(wire, event.bookId().toString()));
            assertThrows(IllegalArgumentException.class,
                    () -> BookAvroMapper.decodeForKey(wire, UUID.randomUUID().toString()));
        }
    }
}
