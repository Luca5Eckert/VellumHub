package com.vellumhub.schema.poc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

/** Deterministic public Book fixtures; never use production or user records. */
final class BookSchemaFixtures {
    static final String TOPIC = "book-events.schema-poc";
    static final String NATIVE_TOPIC = "book-events.schema-poc-native";
    static final String EVENT_ID = "ab482db1-078e-4c95-ab08-772b83ae0679";
    static final String BOOK_ID = "468cb3b6-9b05-49f9-ae4c-e0fae4367e6b";

    private BookSchemaFixtures() {}

    static com.vellumhub.kafka.avro.book.BookChangedEvent specificV2Event(long revision) {
        var state = com.vellumhub.kafka.avro.book.BookState.newBuilder()
                .setTitle("A Book")
                .setDescription(null)
                .setReleaseYear(2024)
                .setCoverUrl(null)
                .setAuthor("An Author")
                .setIsbn(null)
                .setPageCount(288)
                .setPublisher(null)
                .setGenres(List.of("fiction"))
                .build();
        return com.vellumhub.kafka.avro.book.BookChangedEvent.newBuilder()
                .setEventId(EVENT_ID)
                .setOccurredAt("2026-11-06T12:00:00Z")
                .setBookId(BOOK_ID)
                .setRevision(revision)
                .setKind(com.vellumhub.kafka.avro.book.BookChangeKind.UPDATED)
                .setState(state)
                .setCatalogSource(null)
                .build();
    }

    static Schema schema(String resource) {
        try (InputStream stream = BookSchemaFixtures.class.getResourceAsStream("/history/" + resource)) {
            if (stream == null) throw new IllegalArgumentException("Schema fixture not found: " + resource);
            return new Schema.Parser().parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read schema: " + resource, e);
        }
    }

    static GenericRecord event(Schema schema, String kind, long revision, boolean withState) {
        GenericRecord event = new GenericData.Record(schema);
        event.put("eventId", EVENT_ID);
        event.put("bookId", BOOK_ID);
        event.put("occurredAt", "2026-11-06T12:00:00Z");
        event.put("revision", revision);
        event.put("kind", new GenericData.EnumSymbol(schema.getField("kind").schema(), kind));
        if (withState) {
            Schema stateSchema = schema.getField("state").schema().getTypes().stream()
                    .filter(type -> type.getType() == Schema.Type.RECORD).findFirst().orElseThrow();
            GenericRecord state = new GenericData.Record(stateSchema);
            state.put("title", "A Book");
            state.put("description", null);
            state.put("releaseYear", 2024);
            state.put("coverUrl", null);
            state.put("author", "An Author");
            state.put("isbn", null);
            state.put("pageCount", 288);
            state.put("publisher", null);
            state.put("genres", List.of("fiction"));
            event.put("state", state);
        } else {
            event.put("state", null);
        }
        if (schema.getField("catalogSource") != null) event.put("catalogSource", null);
        if (schema.getField("language") != null) event.put("language", null);
        return event;
    }
}
