package com.vellumhub.kafka.avro;

import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ReadingProgressAvroMapperTest {
    @Test void preservesAllFieldsAndIdentityAcrossWire() {
        var event = new ReadingProgressChangedEvent(UUID.randomUUID(),
                Instant.parse("2026-11-10T12:00:00Z"), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "READING", 4, 12, 3, ReadingProgressChangedEvent.Kind.UPDATED);
        var wire = ReadingProgressAvroMapper.toAvro(event);
        assertEquals(event, ReadingProgressAvroMapper.fromAvro(event.bookProgressId().toString(), wire));
        assertThrows(IllegalArgumentException.class, () ->
                ReadingProgressAvroMapper.fromAvro(UUID.randomUUID().toString(), wire));
    }
    @Test void baselineAndCreationKeepTheirOriginalRevisions() {
        for (var kind : new ReadingProgressChangedEvent.Kind[]{
                ReadingProgressChangedEvent.Kind.BASELINE, ReadingProgressChangedEvent.Kind.CREATED}) {
            var event = new ReadingProgressChangedEvent(UUID.randomUUID(), Instant.EPOCH,
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "TO_READ",
                    0, 0, 0, kind);
            assertEquals(event, ReadingProgressAvroMapper.fromAvro(event.bookProgressId().toString(),
                    ReadingProgressAvroMapper.toAvro(event)));
        }
    }
}
