package com.vellumhub.kafka.avro;

import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ReadingProgressAvroMapperTest {
    @Test void preservesAllFieldsAndIdentityAcrossWire() {
        var event = new ReadingProgressChangedEvent(UUID.randomUUID(),
                Instant.parse("2026-11-10T12:00:00Z"), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "READING", 4, 12, 3, ReadingProgressChangedEvent.Kind.UPDATED,
                Instant.parse("2026-11-09T12:00:00Z"), LocalDate.parse("2026-11-30"));
        var wire = ReadingProgressAvroMapper.toAvro(event);
        assertEquals(event, ReadingProgressAvroMapper.fromAvro(event.bookProgressId().toString(), wire));
        assertThrows(IllegalArgumentException.class, () ->
                ReadingProgressAvroMapper.fromAvro(UUID.randomUUID().toString(), wire));
    }
    @Test void lifecycleFieldsStayCompatibleWithThePreviousReaderAndWriter() {
        var current = com.vellumhub.kafka.avro.readingprogress.ReadingProgressChangedEvent.getClassSchema();
        var previous = Schema.createRecord(current.getName(), current.getDoc(), current.getNamespace(), false);
        previous.setFields(current.getFields().stream()
                .filter(field -> !field.name().equals("readAt") && !field.name().equals("targetEndDate"))
                .map(field -> new Schema.Field(field, field.schema()))
                .toList());

        assertEquals(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE,
                SchemaCompatibility.checkReaderWriterCompatibility(current, previous).getType());
        assertEquals(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE,
                SchemaCompatibility.checkReaderWriterCompatibility(previous, current).getType());
    }

    @Test void newStatusesRoundTripAsUpdatesOnTheSameStream() {
        for (String status : new String[]{"PAUSED", "READING", "COMPLETED", "ABANDONED"}) {
            var event = new ReadingProgressChangedEvent(UUID.randomUUID(), Instant.EPOCH,
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), status,
                    12, 12, 2, ReadingProgressChangedEvent.Kind.UPDATED);
            assertEquals(event, ReadingProgressAvroMapper.fromAvro(event.bookProgressId().toString(),
                    ReadingProgressAvroMapper.toAvro(event)));
        }
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
