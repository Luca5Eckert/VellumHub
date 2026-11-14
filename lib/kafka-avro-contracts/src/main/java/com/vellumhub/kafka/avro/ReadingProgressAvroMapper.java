package com.vellumhub.kafka.avro;

import com.vellumhub.kafka.avro.readingprogress.ReadingProgressKind;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import java.time.Instant;
import java.util.UUID;

/** Stable conversion between the integration fact and the approved Avro wire contract. */
public final class ReadingProgressAvroMapper {
    private ReadingProgressAvroMapper() {}

    public static com.vellumhub.kafka.avro.readingprogress.ReadingProgressChangedEvent toAvro(
            ReadingProgressChangedEvent event) {
        return com.vellumhub.kafka.avro.readingprogress.ReadingProgressChangedEvent.newBuilder()
                .setEventId(event.eventId().toString())
                .setOccurredAt(event.occurredAt().toString())
                .setBookProgressId(event.bookProgressId().toString())
                .setUserId(event.userId().toString())
                .setBookId(event.bookId().toString())
                .setProgress(event.progress()).setOldPage(event.oldPage())
                .setNewPage(event.newPage()).setRevision(event.revision())
                .setKind(ReadingProgressKind.valueOf(event.kind().name())).build();
    }

    public static ReadingProgressChangedEvent fromAvro(
            String key, com.vellumhub.kafka.avro.readingprogress.ReadingProgressChangedEvent wire) {
        if (wire == null) throw new IllegalArgumentException("Missing Reading Progress event");
        UUID progressId = UUID.fromString(wire.getBookProgressId());
        if (!progressId.toString().equals(key))
            throw new IllegalArgumentException("Reading Progress key does not match aggregate");
        if (wire.getOldPage() < 0 || wire.getNewPage() < 0)
            throw new IllegalArgumentException("Negative reading page");
        return new ReadingProgressChangedEvent(
                UUID.fromString(wire.getEventId()), Instant.parse(wire.getOccurredAt()),
                progressId, UUID.fromString(wire.getUserId()), UUID.fromString(wire.getBookId()),
                wire.getProgress(), wire.getOldPage(), wire.getNewPage(), wire.getRevision(),
                ReadingProgressChangedEvent.Kind.valueOf(wire.getKind().name()));
    }
}
